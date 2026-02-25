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
@file:Suppress("DEPRECATION")

package io.matthewnelson.kmp.process

import kotlin.test.*

@OptIn(InternalProcessApi::class)
class ReadBufferLineLineOutputFeedUnitTest {

    @Test
    fun givenData_whenCRorCRLF_thenAreIgnored() {
        val buf = ReadBuffer.allocate()

        val b = buildString {
            append("Hello\r\n")
            append("World\n")
            append("Hello\n")
            append("World\r\n")
        }.encodeToByteArray()

        b.forEachIndexed { index, byte -> buf.buf[index] = byte }

        var i = 0
        val expected = listOf("Hello", "World", "Hello", "World", null)
        val scanner = ReadBuffer.lineOutputFeed { line ->
            assertEquals(expected[i++], line)
        }

        scanner.onData(buf, b.size)
        scanner.close()
        assertEquals(expected.size, i)
    }

    @Test
    fun givenUnterminated_whenDoFinal_thenAssumesTermination() {
        val expected = "Not terminated"
        val buf = ReadBuffer.allocate()
        val b = expected.encodeToByteArray()
        b.forEachIndexed { index, byte -> buf.buf[index] = byte }

        var actual: String? = null
        var nullTerminated = false
        val scanner = ReadBuffer.lineOutputFeed { line ->
            if (line == null) {
                nullTerminated = true
            } else {
                actual = line
            }
        }

        scanner.onData(buf, b.size)
        assertNull(actual)
        assertFalse(nullTerminated)

        scanner.close()
        assertNotNull(actual)
        assertTrue(nullTerminated)
        assertEquals(expected, actual)
    }
}
