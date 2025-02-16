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
import io.matthewnelson.kmp.process.*
import io.matthewnelson.kmp.process.internal.fork.posixDup2
import io.matthewnelson.kmp.process.internal.fork.posixExecve
import io.matthewnelson.kmp.process.internal.fork.posixFork
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.spawn.posixSpawnP
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlinx.cinterop.*
import org.kotlincrypto.bitops.endian.Endian.Big.beIntAt
import org.kotlincrypto.bitops.endian.Endian.Big.bePackIntoUnsafe
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
        handler: ProcessException.Handler,
    ): Process {
        val handle = stdio.openHandle()

        try {
            return posixSpawn(command, args, chdir, env, handle, destroy, handler)
        } catch (_: UnsupportedOperationException) {
            /* ignore and try fork/exec */
        } catch (e: IOException) {
            throw handle.tryCloseSuppressed(e)
        }

        try {
            return forkExec(command, args, chdir, env, handle, destroy, handler)
        } catch (e: Exception) {
            handle.tryCloseSuppressed(e)
            throw e.wrapIOException()
        }
    }

    // internal for testing
    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun posixSpawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
        handler: ProcessException.Handler,
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

            val (program, isAbsolutePath) = if (command.contains(SysDirSep)) {
                // File system separator present, ensure it is absolute
                // and normalized. Will use posix_spawn instead of the
                // p variant.
                command.toFile().absoluteFile.normalize().path to true
            } else {
                command to false
            }

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

            if (isAbsolutePath) {
                posixSpawn(program, pid.ptr, fileActions, attrs, argv, envp)
            } else {
                posixSpawnP(program, pid.ptr, fileActions, attrs, argv, envp)
            }.check()

            // If there was a failure in the pre-exec or exec steps, the
            // pid reference will not be modified.
            //
            // Something like using an invalid directory location (non-existent)
            // for chdir would result in this scenario.
            val pv = pid.value
            if (pv == -1) {
                val name = if (isAbsolutePath) "posix_spawn" else "posix_spawnp"
                throw IOException("$name failed in pre-exec/exec step. Bad arguments for '$program'?")
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
            handler,
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
        handler: ProcessException.Handler,
    ): NativeProcess = forkExec(command, command.toProgramPaths(), args, chdir, env, handle, destroy, handler)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    private fun forkExec(
        command: String,
        programPaths: Set<String>,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): NativeProcess {
        val pipe = try {
            Stdio.Pipe.fdOpen()
        } catch (e: IOException) {
            throw handle.tryCloseSuppressed(e)
        }

        val pid = try {
            posixFork().check()
        } catch (e: Exception) {
            handle.tryCloseSuppressed(e)
            pipe.tryCloseSuppressed(e)
            throw e
        }

        if (pid == 0) {
            ChildProcess(pid, pipe, handle, programPaths, args, chdir, env)
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
            handler,
        )

        try {
            pipe.write.close()
        } catch (e: IOException) {
            // If we cannot close the write end of the pipe then
            // read will never pop out with a value of 0 when the
            // child process' exec is successful.
            p.destroy()
            pipe.read.tryCloseSuppressed(e)
            throw IOException("CLOEXEC pipe failure", e)
        }

        // Below is sort of like vfork on Linux where we
        // wait for the child process exec, but with error
        // validation and cleanup on our end.
        val b = ByteArray(5)
        var threw: IOException? = null

        val read = try {
            ReadStream.of(pipe).read(b)
        } catch (e: IOException) {
            threw = IOException("CLOEXEC pipe failure", e)
        }

        try {
            pipe.read.close()
        } catch (e: IOException) {
            if (threw != null) {
                threw.addSuppressed(e)
            } else {
                threw = IOException("CLOEXEC pipe failure", e)
            }
        }

        threw?.let { p.destroy(); throw it }

        when (read) {
            // execve successful and CLOEXEC pipe's write end
            // was closed in the child process, resulting in the
            // read end here in the parent stopping.
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
                    val errno = b.beIntAt(0)
                    val msg = strerror(errno)?.toKString() ?: "errno: $errno"
                    IOException("Child process $type failure. $msg")
                }
            }

            // should never really happen?
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
        programPaths: Set<String>,
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
            errno.bePackIntoUnsafe(b, destOffset = 0)
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
                val argv = args.toArgv(program = programPaths.first(), scope = this)
                val envp = env.toEnvp(scope = this)

                // Try all potential program paths. First one
                // to successfully execute will win out
                programPaths.forEach { path ->
                    posixExecve(path, argv, envp)
                }

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
