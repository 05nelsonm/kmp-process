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
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.withFd
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.errno

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual abstract class ReadStream private constructor(
    private val descriptor: StdioDescriptor,
): Closeable by descriptor {

    @Throws(IOException::class/*, IndexOutOfBoundsException::class*/)
    actual open fun read(buf: ByteArray, offset: Int, len: Int): Int {
        if (isClosed) throw IOException("ReadStream is closed")
        buf.checkBounds(offset, len)
        if (len == 0) return 0

        @Suppress("RemoveRedundantCallsOfConversionMethods")
        val ret = buf.usePinned { pinned ->
            descriptor.withFd(retries = 10, action = { fd ->
                platform.posix.read(
                    fd,
                    pinned.addressOf(offset),
                    len.convert(),
                ).toInt()
            })
        }

        if (ret < 0) throw errnoToIOException(errno)
        if (ret == 0) return -1
        return ret
    }

    @Throws(IOException::class)
    actual fun read(buf: ByteArray): Int = read(buf, 0, buf.size)

    internal companion object {

        internal fun of(
            pipe: StdioDescriptor.Pipe,
        ): ReadStream = of(pipe.read)

        @Throws(IllegalArgumentException::class)
        internal fun of(
            descriptor: StdioDescriptor,
        ): ReadStream {
            require(descriptor.canRead) { "StdioDescriptor must be readable" }
            return object : ReadStream(descriptor) {}
        }
    }
}
