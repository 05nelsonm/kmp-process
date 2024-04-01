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

import io.matthewnelson.kmp.process.ReadBuffer
import kotlin.concurrent.Volatile

internal class RealLineOutputFeed internal constructor(
    private val dispatch: (line: String?) -> Unit
): ReadBuffer.LineOutputFeed() {

    @Volatile
    private var isClosed: Boolean = false
    private val overflow = ArrayList<ByteArray>(1)
    private var skipLF: Boolean = false

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IllegalStateException::class)
    public override fun onData(buf: ReadBuffer, len: Int) {
        if (isClosed) {
            throw IllegalStateException("LineOutputFeed.isClosed[true]")
        }

        if (buf.capacity() <= 0) return
        buf.capacity().checkBounds(0, len)
        var iNext = 0

        for (i in 0 until len) {
            val b = buf[i]

            if (skipLF) {
                skipLF = false

                if (b == LF) {
                    iNext++
                    continue
                }
            }

            when(b) {
                CR -> skipLF = true
                LF -> {}
                else -> continue
            }

            val line = overflow.consumeAndAppend(buf.decodeToUtf8(iNext, i))
            iNext = i + 1

            dispatch(line)
        }

        if (iNext == len) return

        // last character of input was not terminating,
        // buffer it until more data comes in.
        val remainder = ByteArray(len - iNext) { index -> buf[index + iNext] }
        overflow.add(remainder)
    }

    public override fun close() {
        if (isClosed) return
        isClosed = true
        skipLF = false

        try {
            if (overflow.isNotEmpty()) {
                // Assume implicit line break
                val line = overflow.consumeAndAppend(remainder = "")
                dispatch(line)
            }
        } finally {
            dispatch(null)
        }
    }

    private fun ArrayList<ByteArray>.consumeAndAppend(remainder: String): String {
        if (isEmpty()) return remainder

        var size = remainder.length
        forEach { size += it.size }
        val sb = StringBuilder(size)

        while (isNotEmpty()) {
            val segment = removeAt(0)
            sb.append(segment.decodeToString())
            segment.fill(0)
        }

        sb.append(remainder)

        val s = sb.toString()
        sb.clear()
        repeat(size) { sb.append(' ') }

        return s
    }

    internal companion object {
        internal const val CR: Byte = '\r'.code.toByte()
        internal const val LF: Byte = '\n'.code.toByte()
    }
}
