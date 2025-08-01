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
@file:Suppress("LocalVariableName")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import org.kotlincrypto.bitops.endian.Endian.Big.bePackIntoUnsafe
import platform.posix.EBADF
import platform.posix.EINTR
import platform.posix.ENOENT
import platform.posix.ETXTBSY
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.F_SETFD
import platform.posix.O_CLOEXEC
import platform.posix.O_DIRECTORY
import platform.posix.O_RDONLY
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix._exit
import platform.posix.chdir
import platform.posix.close
import platform.posix.dirent
import platform.posix.dup2
import platform.posix.errno
import platform.posix.execve
import platform.posix.fcntl
import platform.posix.open
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalForeignApi::class)
internal class ChildProcess
@Throws(IllegalArgumentException::class)
internal constructor(
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
        _exit(EXIT_CODE)
    }

    init {
        try {
            pipe.read.close()
        } catch (_: IOException) {}
    }

    init {
        var _errno: Int? = null

        try {
            handle.dup2(action = { fd, newFd ->
                when (dup2(fd, newFd)) {
                    -1 -> {
                        _errno = errno
                        IOException()
                    }
                    else -> null
                }
            })
        } catch (_: IOException) {
            onError(_errno ?: EBADF, ERR_DUP2)
        }
    }

    init {
        val flags = O_RDONLY or O_CLOEXEC or O_DIRECTORY
        var fdDir = -1
        while (true) {
            fdDir = open(FD_DIR, flags, 0)
            if (fdDir != -1) break
            val e = errno
            if (e == EINTR) continue
            onError(e, ERR_FD_CLOEXEC)
        }

        var _errno: Int? = null
        val ret = parseDir(fdDir) { entry ->
            val fd = entry.pointed.d_name.toKString().toIntOrNull()

            when (fd) {
                null,
                STDIN_FILENO,
                STDOUT_FILENO,
                STDERR_FILENO,
                fdDir -> null // no-op
                else -> {
                    val flags = fcntl(fd, F_GETFD)
                    if (flags == -1) {
                        _errno = errno
                        return@parseDir Unit
                    }
                    if ((flags and FD_CLOEXEC) == 0) {
                        if (fcntl(fd, F_SETFD, flags or FD_CLOEXEC) == -1) {
                            _errno = errno
                            return@parseDir Unit
                        }
                    }
                    null
                }
            }
        }

        if (ret != null) {
            // fdopendir failed
            while (true) {
                if (close(fdDir) == -1 && errno == EINTR) continue
                break
            }
            _errno = ret
        }

        if (_errno != null) onError(_errno, ERR_FD_CLOEXEC)
    }

    init {
        if (chdir != null && chdir(chdir.path) == -1) {
            onError(errno, ERR_CHDIR)
        }
    }

    init {
        if (resetSignalMasks() == -1) {
            onError(errno, ERR_SIG_MASK)
        }
    }

    init {
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

    internal companion object {
        // Must be greater than 0, otherwise may mistake a blanked
        // array (all 0's) when validating the error type.
        internal const val ERR_DUP2: Byte       = 1
        internal const val ERR_FD_CLOEXEC: Byte = 2
        internal const val ERR_CHDIR: Byte      = 3
        internal const val ERR_SIG_MASK: Byte   = 4
        internal const val ERR_EXEC: Byte       = 5

        internal const val EXIT_CODE: Int       = 127
    }
}

internal expect inline val ChildProcess.FD_DIR: String

/**
 * [action] return [Unit] to break from loop, or `null` to continue.
 *
 * @return [errno] if `fdopendir` fails, otherwise `null`.
 * */
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun ChildProcess.parseDir(fdDir: Int, action: (CPointer<dirent>) -> Unit?): Int?

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun ChildProcess.resetSignalMasks(): Int
