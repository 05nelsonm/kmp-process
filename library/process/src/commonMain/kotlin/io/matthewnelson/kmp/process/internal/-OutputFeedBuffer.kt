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
package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.OutputFeed
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic

internal class OutputFeedBuffer private constructor(maxSize: Int): OutputFeed.Raw {

    private val maxSize = maxSize.coerceAtLeast(1)
    private val segments = ArrayList<Bit8Array>(10)

    @Volatile
    internal var size = 0
        private set

    @Volatile
    internal var hasEnded = false
        private set

    @Volatile
    internal var maxSizeExceeded = false
        private set

    internal fun update(buf: Bit8Array?, len: Int) {
        if (buf == null) {
            hasEnded = true
            return
        }
        val copyLen = if ((size + len) > maxSize) maxSize - size else len
        if (copyLen <= 0) return

        val data = buf.copyOf(newSize = copyLen)
        segments.add(data)
        size += copyLen
        if (size >= maxSize) maxSizeExceeded = true
    }

    internal fun doFinal(): Output.Data {
        val ret = segments.asOutputData()
        segments.clear()
        size = 0
        hasEnded = false
        maxSizeExceeded = false
        return ret
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(maxSize: Int) = OutputFeedBuffer(maxSize)

        @JvmSynthetic
        internal fun of(options: Output.Options) = OutputFeedBuffer(options.maxBuffer)
    }

    override fun onOutput(data: Output.Data?) = error("Use OutputFeedBuffer.{update/doFinal}")
}
