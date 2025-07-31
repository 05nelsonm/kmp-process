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
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

internal class OutputFeedBuffer private constructor(maxSize: Int): OutputFeed {

    private val maxSize = maxSize.takeIf { it > 1 } ?: 1
    private val lines = mutableListOf<String>()

    @Volatile
    @get:JvmName("size")
    internal var size = 0
        private set

    @Volatile
    @get:JvmName("hasEnded")
    internal var hasEnded: Boolean = false
        private set
    @Volatile
    @get:JvmName("maxSizeExceeded")
    internal var maxSizeExceeded: Boolean = false
        private set

    override fun onOutput(line: String?) {
        if (line == null) {
            hasEnded = true
            return
        }
        if (maxSizeExceeded) return

        // do we need to add a new line character
        // between the previous line and this one
        val newLineChar = if (lines.isNotEmpty()) 1 else 0

        val remaining = maxSize - size - newLineChar
        if ((newLineChar + line.length) > remaining) {
            maxSizeExceeded = true

            if (line.isEmpty()) {
                lines.add(line)
                size += newLineChar
                return
            }

            val truncate = line.substring(0, remaining)
            lines.add(truncate)
            size += newLineChar
            size += truncate.length
        } else {
            lines.add(line)
            size += newLineChar
            size += line.length
        }
    }

    internal fun doFinal(): String {
        val sb = StringBuilder(size)
        lines.joinTo(sb, separator = "\n")
        lines.clear()
        val s = sb.toString()
        sb.clear()
        repeat(size) { sb.append(' ') }
        size = 0
        hasEnded = false
        maxSizeExceeded = false
        return s
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(maxSize: Int) = OutputFeedBuffer(maxSize)

        @JvmSynthetic
        internal fun of(options: Output.Options) = OutputFeedBuffer(options.maxBuffer)
    }
}
