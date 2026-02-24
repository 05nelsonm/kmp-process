/*
 * Copyright (c) 2026 Matthew Nelson
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

import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.kmp.file.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class Utf8LineDispatcherUnitTest {

    @Test
    fun givenInput_whenCRorCRLF_thenAreIgnored() {
        val buf = run {
            val b = buildString {
                append("Hello\r\n")
                append("World\n")
                append("Hello\n")
                append("World\r\n")
            }.encodeToByteArray()
            Bit8Array(b.size) { i -> b[i] }
        }

        var i = 0
        val expected = listOf("Hello", "World", "Hello", "World", null)
        val dispatcher = Utf8LineDispatcher.of { line ->
            assertEquals(expected[i++], line)
        }
        dispatcher.update(offset = 0, len = buf.size(), buf::get)
        dispatcher.doFinal()
        assertEquals(expected.size, i)
    }

    @Test
    fun givenBufferedUnterminatedInput_whenDoFinal_thenAssumesIsFinal() {
        val expected = "Not terminated"
        val buf = run {
            val b = expected.encodeToByteArray()
            Bit8Array(b.size) { i -> b[i] }
        }

        val output = mutableListOf<String?>()
        val dispatcher = Utf8LineDispatcher.of(output::add)

        dispatcher.update(offset = 0, len = buf.size(), buf::get)
        assertEquals(0, output.size)
        assertEquals(false, dispatcher.isClosed(), "isClosed")

        dispatcher.doFinal()
        assertEquals(2, output.size)
        assertEquals(expected, output.first())
        assertEquals(null, output.last())
    }

    @Test
    fun givenDispatch_whenException_thenCloses() {
        val buf = run {
            val b = "Hello World\n".encodeToByteArray()
            Bit8Array(b.size) { i -> b[i] }
        }
        val dispatcher = Utf8LineDispatcher.of { throw IOException() }

        try {
            dispatcher.update(offset = 0, len = buf.size(), buf::get)
            fail("dispatch should have thrown exception")
        } catch (_: IOException) {}

        assertEquals(true, dispatcher.isClosed())
        assertFailsWith<EncodingException> { dispatcher.update(offset = 0, len = 2, buf::get) }
        assertFailsWith<EncodingException> { dispatcher.doFinal() }
    }
}
