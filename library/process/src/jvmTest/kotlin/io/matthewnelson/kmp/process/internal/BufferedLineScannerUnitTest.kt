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
import io.matthewnelson.kmp.process.internal.BufferedLineScanner.Companion.scanLines
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferedLineScannerUnitTest {

    private class TestStream(text: String): ReadStream() {

        private val data = text.encodeToByteArray()

        var read = 0
            private set
        var numReads = 0
            private set

        override fun read(): Int { TODO("Not yet implemented") }

        override fun read(buf: ByteArray, offset: Int, len: Int): Int {
            val remainder = data.size - read
            if (remainder <= 0) throw IOException("exhausted")
            numReads++

            val num = min(remainder, len)

            data.copyInto(buf, offset, read, read + num)
            read += num

            return num
        }
    }

    @Test
    fun givenInputStream_whenOutputExceedsBufferSize_thenScanLinesProducesExpectedOutput() {
        val lines = mutableListOf<String>()
        var onStoppedInvocations = 0

        val bufferSize = 512
        val lengthFirst = (bufferSize * 32) + 1

        val s = TestStream(text = buildString {
            repeat(lengthFirst) { append("a") }
            appendLine()
            appendLine("Hello World!")
            append("END")
        })

        s.scanLines(
            readBufferSize = bufferSize,
            dispatchLine = { line -> lines.add(line) },
            onStopped = {
                onStoppedInvocations++
                assertEquals(3, lines.size)
                assertEquals(lengthFirst, lines.first().length)
                assertEquals("Hello World!", lines[1])
                assertEquals("END", lines.last())
            }
        )

        assertEquals(1, onStoppedInvocations)
        assertEquals((lengthFirst / bufferSize) + 1, s.numReads)
    }

    @Test
    fun givenInputStream_whenBufferSizeExceedsOutput_thenScanLinesProducesExpectedOutput() {
        val lines = mutableListOf<String>()
        var onStoppedInvocations = 0

        val s = TestStream(text = buildString {
            appendLine("Hello World!")
            appendLine("END")
        })

        s.scanLines(
            dispatchLine = { line -> lines.add(line) },
            onStopped = {
                onStoppedInvocations++
                assertEquals(2, lines.size)
                assertEquals("Hello World!", lines.first())
                assertEquals("END", lines.last())
            }
        )

        assertEquals(1, onStoppedInvocations)
        assertEquals(1, s.numReads)
    }

    @Test
    fun givenInputStream_whenBlankLines_thenScanLinesProducesExpectedOutput() {
        val lines = mutableListOf<String>()
        var onStoppedInvocations = 0

        val s = TestStream(text = buildString {
            appendLine("Hello World!")
            repeat(4) { appendLine() }
            appendLine("END")
        })

        s.scanLines(
            dispatchLine = { line -> lines.add(line) },
            onStopped = {
                onStoppedInvocations++
                assertEquals(6, lines.size)
                for (i in 1..4) {
                    assertEquals(0, lines[i].length)
                }
                assertEquals("Hello World!", lines.first())
                assertEquals("END", lines.last())
            }
        )

        assertEquals(1, onStoppedInvocations)
        assertEquals(1, s.numReads)
    }

    @Test
    fun givenInputStream_whenOutputSameSizeAsBuffer_thenScanLinesProducesExpectedOutput() {
        val lines = mutableListOf<String>()
        var onStoppedInvocations = 0

        val expected = "1234"
        val s = TestStream(text = expected)

        s.scanLines(
            readBufferSize = expected.length,
            dispatchLine = { line -> lines.add(line) },
            onStopped = {
                onStoppedInvocations++
                assertEquals(1, lines.size)
                assertEquals(expected, lines.first())
            }
        )

        assertEquals(1, onStoppedInvocations)
        assertEquals(1, s.numReads)
    }

    @Test
    fun givenInputStream_whenClosed_thenOnStopInvoked() {
        val lines = mutableListOf<String>()
        var onStoppedInvocations = 0

        // empty string will simulate a read on a closed stream
        // as it will throw IOException.
        val s = TestStream(text = "")

        s.scanLines(
            dispatchLine = { line -> lines.add(line) },
            onStopped = {
                onStoppedInvocations++
                assertEquals(0, lines.size)
            }
        )

        assertEquals(1, onStoppedInvocations)
        assertEquals(0, s.numReads)
    }
}
