/*
 * Copyright (c) 2025 Matthew Nelson
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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class BlockingUnitTest {

    @Test
    fun givenThreadSleep_whenGreaterThan1second_thenSleepsApproximatelyThatDuration() {
        val duration = 2_400.milliseconds

        // warm up
        repeat(10) {
            measureTime { Blocking.threadSleep(1.milliseconds) }
        }

        val time = measureTime { Blocking.threadSleep(duration) }

        // Cannot be exact b/c we're talking about threads in a test here,
        // but can be close just to catch any critically wrong function logic.
        val min = duration - 5.milliseconds
        val max = duration + 150.milliseconds
        assertTrue(time in min..max, "${time.inWholeMilliseconds}ms")
    }

    @Test
    fun givenThreadSleep_whenLessThan1Second_thenSleepsApproximatelyThatDuration() {
        val duration = 50.milliseconds

        // warm up
        repeat(10) {
            measureTime { Blocking.threadSleep(1.milliseconds) }
        }

        val time = measureTime { Blocking.threadSleep(duration) }

        // Cannot be exact b/c we're talking about threads in a test here,
        // but can be close just to catch any critically wrong function logic.
        val min = duration - 5.milliseconds
        val max = duration + 15.milliseconds
        assertTrue(time in min..max, "${time.inWholeMilliseconds}ms")
    }

    @Test
    fun givenThreadSleep_whenNegative_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> { Blocking.threadSleep((-1).milliseconds) }
    }
}
