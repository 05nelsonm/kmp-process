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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.SegmentedData
import io.matthewnelson.kmp.process.internal.asOutputData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OutputDataUnitTest {

    // A Byte.MAX_VALUE sized Output.Data whereby values are their Output.Data index.
    private fun testData(): SegmentedData {
        var i = 0
        val arrays = mutableListOf<Bit8Array>()
        fun addArray(size: Int) {
            val a = Bit8Array(size) { (i++).toByte() }
            arrays.add(a)
        }

        while (i < Byte.MAX_VALUE - 16) {
            addArray(size = Random.nextInt(from = 1, until = 16))
        }
        if (i < Byte.MAX_VALUE) {
            addArray(size = Byte.MAX_VALUE - i)
        }
        return arrays.asOutputData() as SegmentedData
    }

    @Test
    fun givenTestSegmentedData_whenGenerated_thenValuesAreEqualToTheirIndex() {
        // Test our testData function...
        val data = testData()
        assertEquals(Byte.MAX_VALUE.toInt(), data.size)
        var i = 0
        data.segments.forEach { segment ->
            for (j in segment.indices()) {
                assertEquals((i++).toByte(), segment[j])
            }
        }
    }

    @Test
    fun givenSegmentedData_whenGet_thenReturnsTheCorrectValue() {
        val data = testData()
        repeat(Byte.MAX_VALUE.toInt()) { i ->
            assertEquals(i.toByte(), data[i])
        }
        assertFailsWith<IndexOutOfBoundsException> { data[-1] }
        assertFailsWith<IndexOutOfBoundsException> { data[Byte.MAX_VALUE + 1] }
    }

    @Test
    fun givenSegmentedData_whenIterator_thenWorksAsExpected() {
        var i = 0
        val iterator = testData().iterator()
        while (iterator.hasNext()) {
            assertEquals((i++).toByte(), iterator.nextByte())
        }
        assertEquals(Byte.MAX_VALUE.toInt(), i)
        assertFailsWith<NoSuchElementException> { iterator.nextByte() }
    }

    @Test
    fun givenSegmentedData_whenCopyInto_thenCopiesCorrectIndices() {
        val data = testData()
        val dest = ByteArray(data.size) { Byte.MIN_VALUE }

        arrayOf(
            0 to data.size,
            0 to 3,
            5 to 42,
            32 to 88,
            18 to 19,
            25 to 27,
            92 to Byte.MAX_VALUE.toInt(),
        ).forEach { (start, end) ->
            data.copyInto(dest, destOffset = start, indexStart = start, indexEnd = end)
            var i = 0
            while (i < start) {
                assertEquals(Byte.MIN_VALUE, dest[i++])
            }
            for (j in start until end) {
                val e = j.toByte()
                val a = dest[i++]
                assertEquals(e, a, "index[$j]")
            }
            while (i < dest.size) {
                assertEquals(Byte.MIN_VALUE, dest[i++])
            }
            assertEquals(dest.size, i)
            dest.fill(Byte.MIN_VALUE)
        }

        // Double check destOffset works as intended
        data.copyInto(dest, destOffset = 0, indexStart = 42, indexEnd = 88)

        var i = 0
        for (j in 42 until 88) {
            val e = j.toByte()
            val a = dest[i++]
            assertEquals(e, a, "index[${i - 1}]")
        }
        while (i < dest.size) {
            assertEquals(Byte.MIN_VALUE, dest[i++])
        }
        assertEquals(dest.size, i)

        // Yes it is calling checkBounds under the hood
        assertFailsWith<IndexOutOfBoundsException> { data.copyInto(dest, indexStart = -1) }
    }
}
