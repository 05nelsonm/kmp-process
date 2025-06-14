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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.withFd
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned

internal actual abstract class WriteStream private constructor(
    private val descriptor: StdioDescriptor,
) {

    internal val isClosed: Boolean get() = descriptor.isClosed

    //@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    actual open fun write(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (descriptor.isClosed) throw IOException("WriteStream is closed")
        if (len == 0) return

        @Suppress("RemoveRedundantCallsOfConversionMethods")
        @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
        buf.usePinned { pinned ->
            var written = 0
            while (written < len) {

                val write = descriptor.withFd(retries = 10, action = { fd ->
                    platform.posix.write(
                        fd,
                        pinned.addressOf(offset + written),
                        (len - written).convert(),
                    ).toInt()
                }).check()

                if (write == 0) throw IOException("write == 0")

                written += write
            }
        }
    }

    //@Throws(IOException::class)
    actual fun write(buf: ByteArray) { write(buf, 0, buf.size) }

    //@Throws(IOException::class)
    actual open fun close() { descriptor.close() }

    //@Throws(IOException::class)
    actual open fun flush() {}

    internal companion object {

        internal fun of(
            pipe: StdioDescriptor.Pipe,
        ): WriteStream = of(pipe.write)

        @Throws(IllegalArgumentException::class)
        internal fun of(
            descriptor: StdioDescriptor,
        ): WriteStream {
            require(descriptor.canWrite) { "StdioDescriptor must be writable" }
            return object : WriteStream(descriptor) {}
        }
    }
}
