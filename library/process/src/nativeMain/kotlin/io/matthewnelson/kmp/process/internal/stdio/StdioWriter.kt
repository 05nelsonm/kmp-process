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
import io.matthewnelson.kmp.process.internal.check
import io.matthewnelson.kmp.process.internal.checkBounds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned

internal class StdioWriter internal constructor(private val pipe: StdioDescriptor.Pair) {

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    internal fun write(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (pipe.isClosed) throw IOException("StdioWriter is closed")
        if (len == 0) return

        @OptIn(ExperimentalForeignApi::class)
        buf.usePinned { pinned ->
            var written = 0
            while (written < len) {
                val write = platform.posix.write(
                    pipe.fdWrite,
                    pinned.addressOf(offset + written),
                    (len - written).convert()
                ).toInt().check()
                written += write
            }

        }
    }

    @Throws(IOException::class)
    internal fun write(buf: ByteArray) { write(buf, 0, buf.size) }
}
