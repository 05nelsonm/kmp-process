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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalProcessApi::class)
class OutputFeedBufferUnitTest {

    @Test
    fun givenMaxSizeLessThan1_whenOf_thenSizeIs1() {
        val buf = ReadBuffer.allocate()
        arrayOf(0, -1).forEach { maxSize ->
            val feed = OutputFeedBuffer.of(maxSize)
            feed.onData(buf, len = 10)
            assertEquals(1, feed.size)
        }
    }

    @Test
    fun givenOnData_whenLenLessThan1_thenIgnores() {
        val feed = OutputFeedBuffer.of(maxSize = 5)
        val buf = ReadBuffer.allocate()
        assertEquals(0, feed.size)
        feed.onData(buf, len = 0)
        assertEquals(0, feed.size)
        feed.onData(buf, len = -1)
        assertEquals(0, feed.size)
        val data = feed.doFinal()
        assertEquals(0, data.size)
    }

    @Test
    fun givenMaxSize_whenOutputExceeded_thenIgnoresFurtherOutput() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        val buf = ReadBuffer.allocate()
        feed.onData(buf, len = 9)
        assertEquals(9, feed.size)
        feed.onData(buf, len = 2)
        assertEquals(10, feed.size)
        assertEquals(true, feed.maxSizeExceeded)
        assertEquals(10, feed.doFinal().size)
    }

    @Test
    fun givenOnData_whenNull_thenSetsHasEnded() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        assertEquals(false, feed.hasEnded)
        feed.onData(null, -1)
        assertEquals(true, feed.hasEnded)
    }

    @Test
    fun givenNoOutput_whenDoFinal_thenReturnsEmptyData() {
        val data = OutputFeedBuffer.of(maxSize = 2).doFinal()
        assertEquals(0, data.size)
        assertEquals("EmptyData", data::class.simpleName)
    }

    @Test
    fun givenSingleOutput_whenDoFinal_thenReturnsSingleData() {
        val feed = OutputFeedBuffer.of(maxSize = 5)
        val buf = ReadBuffer.allocate()
        feed.onData(buf, len = 6)

        val data = feed.doFinal()
        assertEquals(5, data.size)
        assertEquals("SingleData", data::class.simpleName)
    }

    @Test
    fun givenMultipleOutput_whenDoFinal_thenReturnsSegmentedData() {
        val feed = OutputFeedBuffer.of(maxSize = 10)
        val buf = ReadBuffer.allocate()
        feed.onData(buf, len = 2)
        feed.onData(buf, len = 11)
        val data = feed.doFinal()
        assertEquals(10, data.size)
        assertEquals("SegmentedData", data::class.simpleName)
    }
}
