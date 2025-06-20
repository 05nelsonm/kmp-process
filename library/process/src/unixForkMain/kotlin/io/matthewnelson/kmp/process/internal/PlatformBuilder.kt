/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import org.kotlincrypto.bitops.endian.Endian.Big.beIntAt
import org.kotlincrypto.bitops.endian.Endian.Big.bePackIntoUnsafe
import platform.posix.EBADF
import platform.posix.ENOENT
import platform.posix.ETXTBSY
import platform.posix._exit
import platform.posix.chdir
import platform.posix.dup2
import platform.posix.errno
import platform.posix.execve
import platform.posix.fork
import platform.posix.strerror
import kotlin.time.Duration.Companion.milliseconds

// unixForkMain
internal actual class PlatformBuilder private actual constructor() {

    internal var usePosixSpawn: Boolean = true

    internal actual val env: MutableMap<String, String> by lazy { parentEnvironment() }

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
        val process = if (usePosixSpawn) {
            posixSpawn(
                command,
                args,
                chdir,
                env,
                stdio,
                destroy,
                handler,
            )
        } else {
            null
        }

        if (process != null) return process

        // posix_spawn was:
        //  - Not supported
        //  - There was some sort of initialization failure
        //  - It was disabled by usePosixSpawn variable
        //
        // Try fork/exec.
        return forkExec(command, args, chdir, env, stdio, destroy, handler)
    }

    @Throws(IOException::class)
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun forkExec(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): NativeProcess {
        val handle = stdio.openHandle()

        val pipe = try {
            Stdio.Pipe.fdOpen(readEndNonBlock = true)
        } catch (e: IOException) {
            throw handle.tryCloseSuppressed(e)
        }

        val pid = try {
            fork().check()
        } catch (e: IOException) {
            handle.tryCloseSuppressed(e)
            pipe.tryCloseSuppressed(e)
            throw e
        }

        if (pid == 0) {
            ChildProcess(pid, pipe, handle, command, args, chdir, env)
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

        val buf = ByteArray(5)
        val read = p.awaitExecOrFailure(buf, pipe, onNonZeroExitCode = { null })

        when (read) {
            // execve successful and CLOEXEC pipe's write end
            // was closed in the child process, resulting in the
            // read end here in the parent stopping.
            0 -> null

            // Something happened in the child process
            buf.size -> {
                val type = when (buf[4]) {
                    ERR_DUP2 -> "dup2"
                    ERR_CHDIR -> "chdir"
                    ERR_EXEC -> "exec"
                    else -> null
                }

                if (type == null) {
                    IOException("CLOEXEC pipe validation check failure")
                } else {
                    val errno = buf.beIntAt(0)
                    @OptIn(ExperimentalForeignApi::class)
                    var msg = strerror(errno)?.toKString() ?: "errno: $errno"
                    msg = "Child process $type failure. $msg"
                    if (errno == ENOENT) {
                        FileNotFoundException(msg)
                    } else {
                        IOException(msg)
                    }
                }
            }

            // This should only ever be the scenario when pipe1 is being used (iOS/macOS)
            else -> when (p.exitCodeOrNull()) {
                // pipe1 and we timed-out waiting for the read
                EXIT_127 -> IOException("Invalid read[$read] on CLOEXEC pipe")
                // Either it's still running (null) or the program
                // exited by some manner other than 127.
                else -> null
            }
        }?.let { e: IOException ->
            throw p.destroySuppressed(e)
        }

        return p
    }

    private inner class ChildProcess
    @Throws(IllegalArgumentException::class)
    constructor(
        pid: Int,
        private val pipe: StdioDescriptor.Pipe,
        private val handle: StdioHandle,
        command: String,
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
            _exit(EXIT_127)
        }

        init {
            try {
                pipe.read.close()
            } catch (_: IOException) {}

            var err: Int? = null

            try {
                handle.dup2(action = { fd, newFd ->
                    when (dup2(fd, newFd)) {
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
                val argv = args.toArgv(command = command, scope = this)
                val envp = env.toEnvp(scope = this)

                if (command.contains(SysDirSep)) {
                    // Relative path to w/e the CWD is, or absolute.
                    execve(command, argv, envp)
                    return@memScoped errno
                }

                // Search for the programs using PATH. First to execute wins.
                // Unfortunately execvpe is not available on some platforms, so
                // we do it here manually...
                var _errno = ENOENT
                PATHIterator().forEach { path ->
                    if (path.isBlank()) return@forEach

                    val arg0 = path.toFile().resolve(command).path
                    execve(arg0, argv, envp)

                    _errno = errno
                    if (_errno == ETXTBSY) {
                        try {
                            3.milliseconds.threadSleep()
                        } catch (_: InterruptedException) {}
                        execve(arg0, argv, envp)
                        _errno = errno
                    }
                }

                // exec failed to replace child process with program
                _errno
            }

            onError(errno, ERR_EXEC)
        }
    }

    internal actual companion object {

        // Must be greater than 0, otherwise may mistake a blanked
        // array (all 0's) when validating the error type.
        private const val ERR_DUP2: Byte = 1
        private const val ERR_CHDIR: Byte = 2
        private const val ERR_EXEC: Byte = 3

        private const val EXIT_127: Int = 127

        internal actual fun get(): PlatformBuilder = PlatformBuilder()
    }
}
