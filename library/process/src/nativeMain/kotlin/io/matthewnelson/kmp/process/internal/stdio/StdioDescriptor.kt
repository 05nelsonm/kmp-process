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

package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.Closeable
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*
import kotlin.concurrent.Volatile

internal class StdioDescriptor private constructor(
    private val fd: Int,
    internal val canRead: Boolean,
    internal val canWrite: Boolean,
): Closeable {

    @Volatile
    private var _isClosed: Boolean = false
    override val isClosed: Boolean get() = _isClosed

    @Throws(IOException::class)
    override fun close() {
        if (_isClosed) return

        val result = when (fd) {
            STDIN_FILENO,
            STDOUT_FILENO,
            STDERR_FILENO -> { 0 /* do not actually close */ }
            else -> withFd(retries = 10, action =  { fd -> close(fd) })
        }

        _isClosed = true

        if (result != 0) {
            @OptIn(ExperimentalForeignApi::class)
            throw errnoToIOException(errno)
        }
    }

    @Throws(IOException::class)
    internal fun withFd(
        retries: Int = 3,
        action: (fd: Int) -> Int,
    ): Int {
        val tries = if (retries < 3) 3 else retries
        var eintr = 0

        while (eintr++ < tries) {
            if (_isClosed) throw IOException("StdioDescriptor is closed")

            val result = action(fd)
            if (result == -1 && errno == EINTR) {
                // retry
                continue
            }

            // non-EINTR result
            return result
        }

        // retries ran out
        return -1
    }

    internal companion object {

        internal val STDIN get() = StdioDescriptor(STDIN_FILENO, canRead = false, canWrite = true)
        internal val STDOUT get() = StdioDescriptor(STDOUT_FILENO, canRead = true, canWrite = false)
        internal val STDERR get() = StdioDescriptor(STDERR_FILENO, canRead = true, canWrite = false)

        @Throws(IOException::class)
        internal fun Stdio.File.fdOpen(isStdin: Boolean): StdioDescriptor {
            var mode = 0
            var flags = if (isStdin) O_RDONLY else O_WRONLY

            if (!isStdin && append) {
                flags = flags or O_APPEND
            }

            flags = flags.orOCloExec()

            if (!isStdin && file != STDIO_NULL) {
                // File may need to be created if it does not currently exist
                mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH
                flags = flags or O_CREAT
            }

            val fd = open(file.path, flags, mode).check()
            return StdioDescriptor(fd, canRead = isStdin, canWrite = !isStdin)
        }
    }

    internal class Pipe private constructor(fdRead: Int, fdWrite: Int): Closeable {

        internal val read = StdioDescriptor(fdRead, canRead = true, canWrite = false)
        internal val write = StdioDescriptor(fdWrite, canRead = false, canWrite = true)

        override val isClosed: Boolean get() = read.isClosed && write.isClosed

        @Throws(IOException::class)
        override fun close() {
            var threw: IOException? = null

            try {
                read.close()
            } catch (e: IOException) {
                threw = e
            }
            try {
                write.close()
            } catch (e: IOException) {
                if (threw != null) e.addSuppressed(threw)
                threw = e
            }

            if (threw == null) return
            throw threw
        }

        internal companion object {

            @Throws(IOException::class)
            internal fun Stdio.Pipe.fdOpen(): Pipe = ::Pipe.fdOpen(this)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun Int.orOCloExec(): Int

@Throws(IOException::class)
internal expect fun ((fdRead: Int, fdWrite: Int) -> StdioDescriptor.Pipe).fdOpen(
    stdio: Stdio.Pipe,
): StdioDescriptor.Pipe
