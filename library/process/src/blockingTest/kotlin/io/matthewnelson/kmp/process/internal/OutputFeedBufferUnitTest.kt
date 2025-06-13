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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputFeedBufferUnitTest {

    @Test
    fun givenFeedBuffer_whenMaxSizeSet_thenReturnsExpected() {
        val expectedSize = 20
        val buf = OutputFeedBuffer.of(expectedSize)
        buf.onOutput("          ")
        buf.onOutput("       ")
        assertFalse(buf.maxSizeExceeded)

        buf.onOutput("123")
        assertTrue(buf.maxSizeExceeded)

        buf.onOutput("")
        assertTrue(buf.maxSizeExceeded)

        buf.doFinal().let { final ->
            val lines = final.lines()
            assertEquals(expectedSize, final.length)
            assertEquals(3, lines.size)
            // last line was truncated
            assertEquals("1", lines.last())
        }

        // lines were blanked after calling doFinal once
        assertTrue(buf.doFinal().isEmpty())

        buf.onOutput("                  abc")
        assertTrue(buf.maxSizeExceeded)
        buf.doFinal().let { final ->
            assertEquals(expectedSize, final.length)
            assertEquals('b', final.last())
            assertEquals(1, final.lines().size)
        }

        buf.onOutput("123")
        assertEquals("123", buf.doFinal())
    }

    @Test
    fun givenBuffer_whenObserveNullLine_thenHasEndedIsTrue() {
        val buf = OutputFeedBuffer.of(20)
        assertFalse(buf.hasEnded)
        buf.onOutput(null)
        assertTrue(buf.hasEnded)
        buf.doFinal()
        assertFalse(buf.hasEnded)
    }
}
