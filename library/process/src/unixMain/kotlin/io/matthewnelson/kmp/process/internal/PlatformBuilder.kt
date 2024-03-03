/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.Closable.Companion.tryCloseSuppressed
import io.matthewnelson.kmp.process.internal.fork.posixDup2
import io.matthewnelson.kmp.process.internal.fork.posixExecve
import io.matthewnelson.kmp.process.internal.fork.posixFork
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlinx.cinterop.*
import org.kotlincrypto.endians.BigEndian
import org.kotlincrypto.endians.BigEndian.Companion.toBigEndian
import platform.posix.*

// unixMain
internal actual class PlatformBuilder private actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        parentEnvironment()
    }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, chdir, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process {

        val program = command.toProgramFile()
        val handle = stdio.openHandle()

        try {
            return posixSpawn(command, program, args, chdir, env, handle, destroy)
        } catch (_: UnsupportedOperationException) {
            /* ignore and try fork/exec */
        } catch (e: IOException) {
            handle.tryCloseSuppressed(e)
            throw e
        }

        try {
            return forkExec(command, program, args, chdir, env, handle, destroy)
        } catch (e: Exception) {
            handle.tryCloseSuppressed(e)
            throw e.wrapIOException()
        }
    }

    // internal for testing
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun posixSpawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess = posixSpawn(command, command.toProgramFile(), args, chdir, env, handle, destroy)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    private fun posixSpawn(
        command: String,
        program: File,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        try {
            GnuLibcVersion.check {
                if (!isAtLeast(major = 2u, minor = 24u)) {
                    // Only Linux glibc 2.24+ posix_spawn supports returning ENOENT
                    // fall back to fork & exec
                    throw UnsupportedOperationException()
                }
                if (chdir != null && !isAtLeast(major = 2u, minor = 29u)) {
                    throw UnsupportedOperationException()
                }
            }
        } catch (_: NullPointerException) {
            throw UnsupportedOperationException("gnu_get_libc_version returned null")
        }

        val pid = memScoped {
            val fileActions = posixSpawnFileActionsInit()

            // try chdir first before anything else
            chdir?.let { fileActions.addchdir_np(it, scope = this).check() }

            val attrs = posixSpawnAttrInit()

            handle.dup2(action = { fd, newFd ->
                // posix_spawn_file_actions_adddup2 returns a non-zero
                // value to indicate the error.
                when (val result = fileActions.adddup2(fd, newFd)) {
                    0 -> null
                    else -> errnoToIOException(result)
                }
            })

            // pre-setting to -1 will allow detection of
            // a post-fork step failure (the best we can do atm).
            val pid = alloc<pid_tVar>().apply { value = -1 }

            val argv = args.toArgv(program = program, scope = this)
            val envp = env.toEnvp(scope = this)

            // error detection only with underlying fork/vfork/clone steps
            posixSpawn(program, pid.ptr, fileActions, attrs, argv, envp).check()

            // if there was a failure in the pre-exec or exec steps, the
            // pid reference will not be modified.
            //
            // Something like using an invalid directory location (non-existent)
            // for chdir would result in this scenario.
            val pv = pid.value
            if (pv == -1) {
                throw IOException("posix_spawn failure in pre-exec/exec step. Bad arguments?")
            }

            pv
        }

        return NativeProcess(
            pid,
            handle,
            command,
            args,
            chdir,
            env,
            destroy,
        )
    }

    // internal for testing
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun forkExec(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess = forkExec(command, command.toProgramFile(), args, chdir, env, handle, destroy)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    private fun forkExec(
        command: String,
        program: File,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        val pipe = try {
            Stdio.Pipe.fdOpen()
        } catch (e: IOException) {
            handle.tryCloseSuppressed(e)
            throw e
        }

        val pid = try {
            posixFork().check()
        } catch (e: Exception) {
            handle.tryCloseSuppressed(e)
            pipe.tryCloseSuppressed(e)
            throw e
        }

        if (pid == 0) {
            ChildProcess(pid, pipe, handle, program, args, chdir, env)
        }

        // Parent process
        val p = NativeProcess(
            pid,
            handle,
            command,
            args,
            chdir,
            env,
            destroy,
        )

        try {
            pipe.write.close()
        } catch (e: IOException) {
            // if we cannot close the write end of the pipe then
            // read will never pop out with a value of 0 when the
            // child process' exec is successful.
            p.destroy()
            pipe.read.tryCloseSuppressed(e)
            throw IOException("CLOEXEC pipe failure", e)
        }

        // Below is sort of like vfork on Linux where we
        // wait for the child process, but with error
        // validation and cleanup on our end.
        val b = ByteArray(5)
        var threw: IOException? = null

        val read = try {
            ReadStream.of(pipe).read(b)
        } catch (e: IOException) {
            threw = e
        }

        try {
            pipe.read.close()
        } catch (e: IOException) {
            if (threw != null) {
                threw.addSuppressed(e)
            } else {
                threw = e
            }
        }

        threw?.let { p.destroy(); throw it }

        when (read) {
            // execve successful and CLOEXEC pipe's write end
            // was closed in the child process, resulting in the
            // read end stopping (no write ends).
            0 -> null

            // Something happened in the child process
            b.size -> {
                val type = when (b[4]) {
                    ERR_DUP2 -> "dup2"
                    ERR_CHDIR -> "chdir"
                    ERR_EXEC -> "exec"
                    else -> null
                }

                if (type == null) {
                    IOException("CLOEXEC pipe validation check failure")
                } else {
                    val errno = BigEndian(b[0], b[1], b[2], b[3]).toInt()
                    val msg = strerror(errno)?.toKString() ?: "errno: $errno"
                    IOException("Child process $type failure. $msg")
                }
            }

            // Bad read on our pipe (should never really happen?)
            else -> IOException("invalid read on CLOEXEC pipe")
        }?.let { e: IOException ->
            p.destroy()
            throw e
        }

        return p
    }

    private inner class ChildProcess
    @Throws(IllegalArgumentException::class)
    constructor(
        pid: Int,
        private val pipe: StdioDescriptor.Pipe,
        private val handle: StdioHandle,
        program: File,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
    ) {

        init {
            require(pid == 0) { "pid must be 0 (the child process of a fork call)" }
        }

        private fun onError(errno: Int, type: Byte) {
            val b = ByteArray(5)
            b[4] = type
            errno.toBigEndian().copyInto(b)
            try {
                WriteStream.of(pipe).write(b)
            } catch (_: IOException) {}
            try {
                handle.close()
            } catch (_: IOException) {}
            try {
                pipe.close()
            } catch (_: IOException) {}
            _exit(1)
        }

        init {
            try {
                pipe.read.close()
            } catch (_: IOException) {}

            var err: Int? = null

            try {
                handle.dup2(action = { fd, newFd ->
                    when (posixDup2(fd, newFd)) {
                        -1 -> {
                            err = errno
                            IOException()
                        }
                        else -> null
                    }
                })
            } catch (_: IOException) {
                onError(err ?: EBADF, ERR_DUP2)
            }

            if (chdir != null && chdir(chdir.path) == -1) {
                onError(errno, ERR_CHDIR)
            }

            @OptIn(ExperimentalForeignApi::class)
            val errno = memScoped {
                val argv = args.toArgv(program = program, scope = this)
                val envp = env.toEnvp(scope = this)

                posixExecve(program, argv, envp)

                // exec failed to replace child process with program
                errno
            }

            onError(errno, ERR_EXEC)
        }
    }

    internal actual companion object {

        private const val ERR_DUP2: Byte = 1
        private const val ERR_CHDIR: Byte = 2
        private const val ERR_EXEC: Byte = 3

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        internal actual fun myPid(): Int = getpid()
    }
}
