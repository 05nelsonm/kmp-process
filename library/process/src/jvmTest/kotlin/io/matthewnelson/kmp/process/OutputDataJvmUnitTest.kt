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
import io.matthewnelson.kmp.process.internal.asOutputData
import io.matthewnelson.kmp.process.internal.empty
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalProcessApi::class)
class OutputDataJvmUnitTest {

    private fun ByteBuffer.assertState(
        param: String,
        expectedCapacity: Int,
        expectedPosition: Int = 0,
        expectedLimit: Int = expectedCapacity,
    ): ByteBuffer {
        assertEquals(true, isReadOnly, "isReadOnly >> $param")
        assertEquals(expectedPosition, position(), "position >> $param")
        assertEquals(expectedCapacity, capacity(), "capacity >> $param")
        assertEquals(expectedLimit, limit(), "limit >> $param")
        return this
    }

    @Test
    fun givenEmptyData_whenAsByteBuffer_thenIsEmpty() {
        Output.Data.empty()
            .asByteBuffer()
            .assertState("bb", expectedCapacity = 0)
    }

    @Test
    fun givenSingleData_whenAsByteBuffer_thenAreDifferentInstances() {
        val data = Bit8Array(20) { i -> (i - 10).toByte() }.asOutputData()
        assertEquals(20, data.size)

        val bb1 = data.asByteBuffer().assertState("bb1", expectedCapacity = data.size)

        // Should not be affected by modifications to bb1
        val bb2 = data.asByteBuffer().assertState("bb2", expectedCapacity = data.size)

        for (i in data.indices) {
            val e = data[i]
            val a = bb1.get()
            assertEquals(e, a, "expected[$e] != actual[$a] >> index[$i]")
        }

        bb1.assertState("bb1", expectedCapacity = data.size, expectedPosition = data.size)
        bb2.assertState("bb2", expectedCapacity = data.size)

        // For posterity, check a newly created one.
        data.asByteBuffer().assertState("bb3", expectedCapacity = data.size)
    }

    @Test
    fun givenSegmentedData_whenAsByteBuffer_thenAreDifferentInstances() {
        val buf = Bit8Array(10) { i -> (i - 10).toByte() }
        val data = listOf(buf, buf).asOutputData()
        assertEquals(20, data.size)

        val bb1 = data.asByteBuffer().assertState("bb1", expectedCapacity = data.size)

        // Should not be affected by modifications to bb1
        val bb2 = data.asByteBuffer().assertState("bb2", expectedCapacity = data.size)

        for (i in data.indices) {
            val e = data[i]
            val a = bb1.get()
            assertEquals(e, a, "expected[$e] != actual[$a] >> index[$i]")
        }

        bb1.assertState("bb1", expectedCapacity = data.size, expectedPosition = data.size)
        bb2.assertState("bb2", expectedCapacity = data.size)

        // For posterity, check a newly created one.
        data.asByteBuffer().assertState("bb3", expectedCapacity = data.size)
    }
}
