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

import io.matthewnelson.kmp.file.IOException
import kotlin.jvm.JvmSynthetic

@Suppress("UNUSED")
internal class BufferedLineScanner private constructor(
    readBufferSize: Int,
    stream: ReadStream,
    dispatchLine: (line: String) -> Unit,
    onStopped: () -> Unit,
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

    init {
        val buf = ByteArray(readBufferSize)

        while (true) {
            val read = try {
                stream.read(buf)
            } catch (_: IOException) {
                break
            }

            // If a pipe has no write ends open (i.e. the
            // child process exited), a zero read is returned,
            // and we can end early (before process destruction).
            if (read <= 0) break

            var iNext = 0
            for (i in 0 until read) {
                if (buf[i] != N) continue
                val line = overflow.consumeAndJoin(append = buf.decodeToString(iNext, i))
                iNext = i + 1

                dispatchLine(line)
            }

            if (iNext == read) continue
            overflow.add(buf.copyOfRange(iNext, read))
        }

        if (overflow.isNotEmpty()) {
            val s = overflow.consumeAndJoin(append = "")
            dispatchLine(s)
        }

        buf.fill(0)

        onStopped()
    }

    internal companion object {

        @JvmSynthetic
        internal fun ReadStream.scanLines(
            dispatchLine: (line: String) -> Unit,
            onStopped: () -> Unit,
        ) { scanLines(1024 * 8, dispatchLine, onStopped) }

        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal fun ReadStream.scanLines(
            readBufferSize: Int,
            dispatchLine: (line: String) -> Unit,
            onStopped: () -> Unit,
        ) { BufferedLineScanner(readBufferSize, this, dispatchLine, onStopped) }

        private const val N = '\n'.code.toByte()
    }
}
