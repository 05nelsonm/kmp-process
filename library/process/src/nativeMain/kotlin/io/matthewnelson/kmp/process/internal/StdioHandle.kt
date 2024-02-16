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

@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Stdio
import platform.posix.*
import platform.posix.close as posix_close
import kotlin.concurrent.Volatile

@OptIn(ExperimentalStdlibApi::class)
internal class StdioHandle private constructor(
    internal val stdio: Stdio.Config,
    private val stdinFD: Int,
    private val stdoutFD: Int,
    private val stderrFD: Int,
): AutoCloseable {

    @Volatile
    private var isClosed = false
    private val lock = Lock()

    // TODO: internal val stdin: Writer?
    // TODO: internal val stdout: Reader?
    // TODO: internal val stderr: Reader?

    @Throws(IOException::class)
    internal fun dup2(block: (fd: Int, newFd: Int) -> IOException?) {
        lock.withLock {
            if (isClosed) throw IOException("StdioHandle is closed")

            try {
                block(stdinFD, STDIN_FILENO)?.let { throw it }
                block(stdoutFD, STDOUT_FILENO)?.let { throw it }
                block(stderrFD, STDERR_FILENO)?.let { throw it }
            } catch (e: IOException) {
                closeNoLock()
                throw e
            }
        }
    }

    override fun close() {
        // subsequent calls to close will do nothing, as
        // we do not want to call close on potentially
        // recycled descriptors.
        if (isClosed) return

        lock.withLock {
            if (isClosed) return@withLock
            closeNoLock()
        }
    }

    private fun closeNoLock() {
        // Only close if NOT Stdio.Inherit
        if (!stdio.stdin.isInherit) {
            posix_close(stdinFD)
        }
        if (!stdio.stdout.isInherit) {
            posix_close(stdoutFD)
        }
        if (!stdio.stderr.isInherit) {
            posix_close(stderrFD)
        }

        // TODO: Close Reader/Writer(s)
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun Stdio.Config.openHandle(): StdioHandle {
            val stdinFd = when (val s = stdin) {
                is Stdio.Inherit -> STDIN_FILENO
                is Stdio.File -> s.openFD(isStdin = true)
                is Stdio.Pipe -> s.openFD(isStdin = true)
            }

            val stdoutFd = when (val s = stdout) {
                is Stdio.Inherit -> STDOUT_FILENO
                is Stdio.File -> s.openFD(isStdin = false)
                is Stdio.Pipe -> s.openFD(isStdin = false)
            }

            val stderrFd = when (val s = stderr) {
                is Stdio.Inherit -> STDERR_FILENO
                is Stdio.File -> s.openFD(isStdin = false)
                is Stdio.Pipe -> s.openFD(isStdin = false)
            }

            return StdioHandle(this, stdinFd, stdoutFd, stderrFd)
        }

        @Throws(IOException::class)
        private fun Stdio.File.openFD(isStdin: Boolean): Int {
            var mode = 0
            var flags = if (isStdin) O_RDONLY else O_WRONLY
            flags = flags or O_CLOEXEC

            if (file != STDIO_NULL && !isStdin) {
                mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH
                flags = flags or O_CREAT
            }
            if (!isStdin && append) {
                flags = flags or O_APPEND
            }

            return open(file.path, flags, mode).check()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline val Stdio.isInherit: Boolean get() = this is Stdio.Inherit
}
