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
import io.matthewnelson.kmp.process.internal.fork.dup2
import io.matthewnelson.kmp.process.internal.fork.execve
import io.matthewnelson.kmp.process.internal.fork.fork
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pair.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioReader
import io.matthewnelson.kmp.process.internal.stdio.StdioWriter
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
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process {

        val program = command.toProgramFile()
        val handle = stdio.openHandle()

        try {
            return posixSpawn(command, program, args, env, handle, destroy)
        } catch (_: UnsupportedOperationException) {
            /* ignore and try fork/exec */
        } catch (e: IOException) {
            handle.close()
            throw e
        }

        try {
            return forkExec(command, program, args, env, handle, destroy)
        } catch (e: Exception) {
            handle.close()
            throw e.wrapIOException()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    private fun posixSpawn(
        command: String,
        program: File,
        args: List<String>,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        try {
            GnuLibcVersion.check {
                if (!isAtLeast(major = 2u, minor = 24u)) {
                    // Only Linux glibc 2.24+ posix_spawn supports returning ENOENT
                    // fall back to fork & exec
                    throw UnsupportedOperationException("Unsupported Linux $this")
                }
                // TODO: Issue #15
                //  if addchdir_np needed, glibc 2.29+ required
            }
        } catch (_: NullPointerException) {
            // gnu_get_libc_version on Linux returned null
            throw UnsupportedOperationException("gnu_get_libc_version returned null")
        }

        val pid = memScoped {
            val fileActions = posixSpawnFileActionsInit()

            // TODO: Issue #15
            //  try addchdir_np (iOS/Linux throws IOException)

            val attrs = posixSpawnAttrInit()

            handle.dup2(action = { fd, newFd ->
                // posix_spawn_file_actions_adddup2 returns a non-zero
                // value to indicate the error.
                when (val result = fileActions.adddup2(fd, newFd)) {
                    0 -> null
                    else -> errnoToIOException(result)
                }
            })

            val pid = alloc<pid_tVar>()
            val argv = args.toArgv(program = program, scope = this)
            val envp = env.toEnvp(scope = this)

            posixSpawn(program, pid.ptr, fileActions, attrs, argv, envp).check()

            pid.value
        }

        return NativeProcess(
            pid,
            handle,
            command,
            args,
            env,
            destroy,
        )
    }

    // internal for testing
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun forkExec(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess = forkExec(command, command.toProgramFile(), args, env, handle, destroy)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    private fun forkExec(
        command: String,
        program: File,
        args: List<String>,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        val pipe = Stdio.Pipe.fdOpen()

        val pid = try {
            fork().check()
        } catch (e: IOException) {
            handle.close()
            pipe.close()
            throw e
        }

        if (pid == 0) {
            // Child process
            close(pipe.fdRead)

            var err: Int? = null

            try {
                handle.dup2 { fd, newFd ->
                    when (dup2(fd, newFd)) {
                        -1 -> {
                            err = errno
                            // thrown (and handle is thus closed)
                            // but catch block from within the child
                            // does not utilize the exception, it
                            // will write errno output back to
                            // parent.
                            IOException()
                        }
                        else -> null
                    }
                }
            } catch (_: IOException) {
                // [#, #, #, #, 1] (1: dup2 failure)
                val b = ByteArray(5)
                b[4] = 1
                (err ?: EBADF).toBigEndian().copyInto(b)
                try {
                    StdioWriter(pipe).write(b)
                } finally {
                    close(pipe.fdWrite)
                }
                _exit(1)
            }

            val errno = memScoped {
                val argv = args.toArgv(program = program, scope = this)
                val envp = env.toEnvp(scope = this)

                execve(program.path, argv, envp)

                // exec failed to replace child process with program
                errno
            }

            // [#, #, #, #, 2] (2: execve failure)
            val b = ByteArray(5)
            b[4] = 2
            errno.toBigEndian().copyInto(b)
            try {
                StdioWriter(pipe).write(b)
            } finally {
                close(pipe.fdWrite)
            }
            _exit(1)
        }

        // Parent process
        close(pipe.fdWrite)

        val p = NativeProcess(
            pid,
            handle,
            command,
            args,
            env,
            destroy,
        )

        // Below is sort of like vfork on Linux, but
        // with error validation and cleanup on our
        // end.
        val b = ByteArray(5)

        val read = try {
            StdioReader(pipe).read(b)
        } catch (e: IOException) {
            p.destroy()
            throw IOException("CLOEXEC pipe read failure", e)
        } finally {
            close(pipe.fdRead)
        }

        when (read) {
            // execve successful and CLOEXEC pipe's write
            // was closed, resulting in the read end stopping.
            0 -> null

            // Something happened in the child process
            b.size -> {
                val type = when (b[4].toInt()) {
                    1 -> "dup2"
                    2 -> "exec"
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

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        internal actual fun myPid(): Int = getpid()
    }
}
