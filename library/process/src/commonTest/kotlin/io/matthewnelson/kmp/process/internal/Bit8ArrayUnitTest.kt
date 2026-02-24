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
import kotlin.test.assertFailsWith

class Bit8ArrayUnitTest {

    private companion object {
        private const val SIZE = 10
    }

    private fun Bit8Array.assertCopyFailure(
        dest: ByteArray,
        destOffset: Int = 0,
        indexStart: Int = 0,
        indexEnd: Int = size(),
    ) {
        assertFailsWith<IndexOutOfBoundsException> {
            copyInto(dest, destOffset, indexStart, indexEnd, checkBounds = true)
        }
    }

    private val b = Bit8Array(SIZE) { i -> (i + 1).toByte() }
    private val dest = ByteArray(b.size())

    @Test
    fun givenCopyInto_whenIndexStartNegative_thenThrowsIndexOutOfBoundsException() {
        b.assertCopyFailure(dest, indexStart = -1)
    }

    @Test
    fun givenCopyInto_whenIndexStartGreaterThanIndexEnd_thenThrowsIndexOutOfBoundsException() {
        b.assertCopyFailure(dest, indexStart = 2, indexEnd = 1)
    }

    @Test
    fun givenCopyInto_whenIndexEndGreaterThanSize_thenThrowsIndexOutOfBoundsException() {
        b.assertCopyFailure(dest, indexEnd = b.size() + 1)
    }

    @Test
    fun givenCopyInto_whenDestCapacityInsufficient_thenThrowsIndexOutOfBoundsException() {
        b.assertCopyFailure(dest, destOffset = b.size() - 3)
    }
}
