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
import io.matthewnelson.kmp.process.internal.InputStream
import io.matthewnelson.kmp.process.internal.check
import io.matthewnelson.kmp.process.internal.checkBounds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned

internal class StdioReader internal constructor(
    private val pipe: StdioDescriptor.Pair
): InputStream() {

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    override fun read(buf: ByteArray, offset: Int, len: Int): Int {
        buf.checkBounds(offset, len)
        if (pipe.isClosed) throw IOException("StdioReader is closed")
        if (len == 0) return 0

        @OptIn(ExperimentalForeignApi::class)
        return buf.usePinned { pinned ->
            platform.posix.read(pipe.fdRead, pinned.addressOf(offset), len.convert()).toInt()
        }.check()
    }
}
