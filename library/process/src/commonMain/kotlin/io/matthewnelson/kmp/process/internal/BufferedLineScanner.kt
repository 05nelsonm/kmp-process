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

internal abstract class BufferedLineScanner(
    private val dispatchLine: (line: String) -> Unit,
) {

    private val overflow = ArrayList<ByteArray>(1)

    private fun ArrayList<ByteArray>.consumeAndJoin(append: String): String {
        if (isEmpty()) return append

        var size = append.length
        forEach { size += it.size }
        val sb = StringBuilder(size)

        while (isNotEmpty()) {
            val segment = removeAt(0)
            sb.append(segment.decodeToString())
            segment.fill(0)
        }

        sb.append(append)

        val s = sb.toString()
        sb.clear()
        repeat(size) { sb.append(' ') }

        return s
    }

    protected fun onData(data: ByteArray, len: Int = data.size) {
        var iNext = 0
        for (i in 0 until len) {
            if (data[i] != N) continue
            val line = overflow.consumeAndJoin(append = data.decodeToString(iNext, i))
            iNext = i + 1

            dispatchLine(line)
        }

        if (iNext == len) return
        overflow.add(data.copyOfRange(iNext, len))
    }

    protected fun onStopped() {
        if (overflow.isEmpty()) return
        val s = overflow.consumeAndJoin(append = "")
        dispatchLine(s)
    }

    internal companion object {

        internal const val N: Byte = '\n'.code.toByte()
    }
}
