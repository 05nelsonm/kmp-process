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

class OutputFeedReaderUnitTest {

    @Test
    fun givenReader_whenMaxSizeSet_thenReturnsExpected() {
        val expectedSize = 20
        val r = OutputFeedBuffer(expectedSize)
        r.onOutput("          ")
        r.onOutput("       ")
        assertFalse(r.maxSizeExceeded)

        r.onOutput("123")
        assertTrue(r.maxSizeExceeded)

        r.onOutput("")
        assertTrue(r.maxSizeExceeded)

        r.doFinal().let { final ->
            val lines = final.lines()
            assertEquals(expectedSize, final.length)
            assertEquals(3, lines.size)
            // last line was truncated
            assertEquals("1", lines.last())
        }

        // lines were blanked after calling doFinal once
        assertTrue(r.doFinal().isEmpty())

        r.onOutput("                  abc")
        assertTrue(r.maxSizeExceeded)
        r.doFinal().let { final ->
            assertEquals(expectedSize, final.length)
            assertEquals('b', final.last())
            assertEquals(1, final.lines().size)
        }

        r.onOutput("123")
        assertEquals("123", r.doFinal())
    }
}
