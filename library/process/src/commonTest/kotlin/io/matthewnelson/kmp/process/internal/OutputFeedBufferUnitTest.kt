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

import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.process.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalProcessApi::class)
class OutputFeedBufferUnitTest {

    @Test
    fun givenBuffer_whenUtilized_thenOperatesAsExpected() {
        val feedBuffer = OutputFeedBuffer.of(maxSize = 10)
        val buf = ReadBuffer.allocate()
        buf[0] = 1
        buf[1] = 2
        feedBuffer.onData(buf, len = 2)
        assertEquals(false, feedBuffer.maxSizeExceeded)
        buf[0] = 3
        buf[1] = 4
        buf[2] = 5
        feedBuffer.onData(buf, len = 3)
        assertEquals(false, feedBuffer.maxSizeExceeded)
        buf[0] = 6
        buf[1] = 7
        buf[2] = 8
        buf[3] = 9
        feedBuffer.onData(buf, len = 4)
        assertEquals(false, feedBuffer.maxSizeExceeded)

        // Should only take 1 byte (maxSize == 10)
        buf[0] = 10
        buf[1] = 11
        feedBuffer.onData(buf, len = 4)
        assertEquals(10, feedBuffer.size)
        assertEquals(true, feedBuffer.maxSizeExceeded)

        // Should do nothing
        buf[0] = 12
        buf[1] = 13
        feedBuffer.onData(buf, len = 4)
        assertEquals(10, feedBuffer.size)
        assertEquals(true, feedBuffer.maxSizeExceeded)

        // hasEnded is set when buf == null
        assertEquals(false, feedBuffer.hasEnded)
        feedBuffer.onData(null, -1)
        assertEquals(true, feedBuffer.hasEnded)

        val final = feedBuffer.doFinal()
        assertEquals(0, feedBuffer.size)
        assertEquals(false, feedBuffer.maxSizeExceeded)
        assertEquals(10, final.length)

        // Output.Buffered.get retrieves byte from the correct ReadBuffer
        var i = 0
        for (j in final.indices) {
            i++
            assertEquals(j + 1, final[j].toInt())
        }
        // Confirm it ripped through 10 bytes
        assertEquals(10, i)
        assertFailsWith<IndexOutOfBoundsException> { final[10] }

        // ByteIterator works as expected
        i = 0
        val iterator = final.iterator()
        while (iterator.hasNext()) {
            assertEquals(++i, iterator.nextByte().toInt())
        }
        // Confirm it ripped through 10 bytes
        assertEquals(10, i)

        assertFailsWith<NoSuchElementException> { iterator.nextByte() }
    }

    @Test
    fun givenBuffer_whenSize0_thenEmptyOutputOperatesAsExpected() {
        val buffer = OutputFeedBuffer.of(2)
        val final = buffer.doFinal()
        assertEquals(OutputFeedBuffer.EMPTY_OUTPUT, final)
        assertEquals(0, final.length)
        var i = 0
        for (j in final.indices) {
            assertEquals(i++, j)
        }
        assertEquals(0, i)
        val iterator = final.iterator()
        assertEquals(false, iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.nextByte() }
    }
}
