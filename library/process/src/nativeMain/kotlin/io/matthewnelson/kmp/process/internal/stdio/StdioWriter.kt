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
package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.StdinStream
import io.matthewnelson.kmp.process.internal.checkBounds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.errno

internal class StdioWriter internal constructor(
    private val pipe: StdioDescriptor.Pair
): StdinStream() {

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    override fun write(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (pipe.isClosed) throw IOException("StdioWriter is closed")
        if (len == 0) return

        @OptIn(ExperimentalForeignApi::class)
        buf.usePinned { pinned ->
            var written = 0
            while (written < len) {

                var write: Int? = null
                var interrupted = 0
                while (write == null && interrupted++ < 3) {
                    val res = platform.posix.write(
                        pipe.fdWrite,
                        pinned.addressOf(offset + written),
                        (len - written).convert(),
                    ).toInt()

                    // TODO: If 0, are all fdRead ends closed?
                    //  not really a problem b/c of isClosed
                    //  check, but should do a proper check here

                    if (res == -1) {
                        when (val e = errno) {
                            EINTR -> continue
                            else -> throw errnoToIOException(e)
                        }
                    }

                    write = res
                }

                // Retried 3 times, all interrupted...
                write ?: throw errnoToIOException(EINTR)

                written += write
            }
        }
    }

    @Throws(IOException::class)
    override fun close() { pipe.close() }

    @Throws(IOException::class)
    override fun flush() {
        // TODO
    }
}
