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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.IsAppleSimulator
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ProcessBlockingUnitTest {

    @Test
    fun givenWaitFor_whenProcessCompletes_thenReturnsEarly() {
        if (IsWindows) {
            println("Skipping...")
            return
        }

        val runTime = measureTime {
            Process.Builder(command = if (IsAppleSimulator) "/bin/sleep" else "sleep")
                .args("0.5")
                .destroySignal(Signal.SIGKILL)
                .useSpawn { p ->
                    assertNull(p.waitFor(100.milliseconds), "100ms")
                    assertTrue(p.isAlive)
                    assertEquals(0, p.waitFor(3.seconds), "exitCode")
                    assertFalse(p.isAlive)
                }
        }

        // Should be less than the 2.5 seconds (waitFor popped out early)
        assertTrue(runTime < 2_500.milliseconds, "runTime[$runTime] > 2.5s")
    }

    @Test
    fun givenWaitFor_whenCompletion_thenReturnsExitCode() {
        val exitCode = try {
            Process.Builder(command = if (IsAppleSimulator) "/bin/sleep" else "sleep")
                .args("0.25")
                .destroySignal(Signal.SIGKILL)
                .useSpawn { p -> p.waitFor() }
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                println("Skipping...")
                return
            }
            throw e
        }

        assertEquals(0, exitCode)
    }
}
