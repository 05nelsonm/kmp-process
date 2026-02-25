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

class OutputFeedBufferUnitTest {

    @Test
    fun givenMaxSizeLessThan1_whenOf_thenSizeIs1() {
        val buf = Bit8Array(2)
        arrayOf(0, -1).forEach { maxSize ->
            val feed = OutputFeedBuffer.of(maxSize)
            feed.update(buf, len = buf.size()/*, isReusableBuffer = false*/)
            assertEquals(1, feed.size)
        }
    }

    @Test
    fun givenUpdate_whenLenIsLessThan1_thenIsIgnored() {
        val feed = OutputFeedBuffer.of(maxSize = 5)
        val buf = Bit8Array(3)
        assertEquals(0, feed.size)

        feed.update(buf, len = 0/*, false*/)
        feed.update(buf, len = 0/*, true*/)
        assertEquals(0, feed.size)

        feed.update(buf, len = -1/*, false*/)
        feed.update(buf, len = -1/*, true*/)
        assertEquals(0, feed.size)

        feed.update(null, -1/*, false*/)
        assertEquals(0, feed.doFinal().size)
    }

    @Test
    fun givenMaxSize_whenExceeded_thenIgnoresFurtherInput() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        val buf = Bit8Array(10) { i -> (i + 1).toByte() }

        feed.update(buf, len = 9/*, isReusableBuffer = true*/)
        assertEquals(9, feed.size)

        feed.update(buf, len = 2/*, isReusableBuffer = true*/)
        assertEquals(10, feed.size)
        assertEquals(true, feed.maxSizeExceeded)

        feed.update(null, -1/*, false*/)
        assertEquals(10, feed.doFinal().size)
    }

    @Test
    fun givenUpdate_whenNull_thenHasEndedIsTrue() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        assertEquals(false, feed.hasEnded)
        feed.update(null, -1/*, true*/)
        assertEquals(true, feed.hasEnded)
    }

    @Test
    fun givenNoInput_whenDoFinal_thenReturnsEmptyData() {
        val feed = OutputFeedBuffer.of(2)
        feed.update(null, -1/*, false*/)
        val data = feed.doFinal()
        assertEquals(0, data.size)
        assertEquals("EmptyData", data::class.simpleName)
    }

    @Test
    fun givenSingleInput_whenDoFinal_thenReturnsSingleData() {
        val feed = OutputFeedBuffer.of(maxSize = 5)
        feed.update(Bit8Array(6), 6/*, isReusableBuffer = false*/)
        feed.update(null, -1/*, false*/)
        val data = feed.doFinal()
        assertEquals(5, data.size)
        assertEquals("SingleData", data::class.simpleName)
    }

    @Test
    fun givenMultipleInput_whenDoFinal_thenReturnsSegmentedData() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        val buf = Bit8Array(15)
        feed.update(buf, len = 2/*, isReusableBuffer = true*/)
        feed.update(buf, len = 11/*, isReusableBuffer = true*/)
        feed.update(null, -1/*, false*/)
        val data = feed.doFinal()
        assertEquals(10, data.size)
        assertEquals("SegmentedData", data::class.simpleName)
    }

    // TODO: test isReusableBuffer functionality (Issue #233)
}
