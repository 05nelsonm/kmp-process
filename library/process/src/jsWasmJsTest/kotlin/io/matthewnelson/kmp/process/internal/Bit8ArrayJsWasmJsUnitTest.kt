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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Bit8ArrayJsWasmJsUnitTest {

    @Test
    fun givenCopyOf_whenNewSizeLessThanOrEqual_thenNewArrayIsPopulatedFromOld() {
        val b1 = Bit8Array(20) { i -> (i + 1).toByte() }
        assertEquals(20, b1.size())

        val b2 = b1.copyOf(newSize = 10)
        assertEquals(10, b2.size())
        var j = 0
        for (i in b2.indices()) {
            j++
            val e = b1[i]
            val a = b2[i]
            assertEquals(e, a, "index[$i]")
        }
        assertEquals(b2.size(), j)

        b2[0] = -10
        // b2 is in fact a new array
        assertNotEquals(b1[0], b2[0])
    }

    @Test
    fun givenCopyOf_whenNewSizeGreaterThan_thenNewArrayIsPopulatedFromOldAndRemaindersAreZero() {
        val b1 = Bit8Array(10) { i -> (i + 1).toByte() }
        assertEquals(10, b1.size())

        val b2 = b1.copyOf(newSize = 20)
        assertEquals(20, b2.size())
        var j = 0
        for (i in b1.indices()) {
            j++
            val e = b1[i]
            val a = b2[i]
            assertEquals(e, a, "index[$i]")
        }
        assertEquals(b1.size(), j)

        for (i in j until b2.size()) {
            j++
            val e = 0.toByte()
            val a = b2[i]
            assertEquals(e, a, "index[$i]")
        }
        assertEquals(b2.size(), j)

        b2[0] = -10
        // b2 is in fact a new array
        assertNotEquals(b1[0], b2[0])
    }

    @Test
    fun givenCopyInto_whenDestOffset_thenCorrectlyPlacesBytes() {
        val bit8 = Bit8Array(10) { it.toByte() }
        val b = ByteArray(bit8.size())
        bit8.copyInto(b, b.lastIndex, 5, 6, checkBounds = true)
        assertEquals(5.toByte(), b.last())
        for (i in 0 until b.lastIndex) {
            assertEquals(0.toByte(), b[i], "index[$i]")
        }
    }
}
