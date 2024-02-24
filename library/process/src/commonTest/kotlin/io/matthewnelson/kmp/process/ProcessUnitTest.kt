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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.IsWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ProcessUnitTest {

    @Test
    fun givenWaitFor_whenProcessCompletes_thenReturnsEarly() {
        if (IsDarwinMobile || IsNodeJs || IsWindows) {
            skipping()
            return
        }

        val runTime = measureTime {
            Process.Builder(command = "sleep")
                .args("0.25")
                .destroySignal(Signal.SIGKILL)
                .spawn { p ->
                    assertNull(p.waitFor(100.milliseconds))
                    assertTrue(p.isAlive)
                    assertEquals(0, p.waitFor(2.seconds))
                    assertFalse(p.isAlive)
                }
        }

        // Should be less than the 2 seconds (waitFor popped out early)
        assertTrue(runTime < 1.seconds)
    }

    @Test
    fun givenWaitFor_whenCompletion_thenReturnsExitCode() {
        if (IsDarwinMobile || IsNodeJs) {
            skipping()
            return
        }

        val exitCode = try {
            Process.Builder(command = "sleep")
                .args("0.25")
                .destroySignal(Signal.SIGKILL)
                .spawn { p ->
                    p.waitFor()
                }
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                skipping()
                return
            }
            throw e
        }

        assertEquals(0, exitCode)
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsStatusCode() {
        if (IsDarwinMobile || IsWindows) {
            skipping()
            return
        }

        val expected = 42
        val actual = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 0.25; exit $expected")
            .destroySignal(Signal.SIGKILL)
            // Should complete and exit before timing out
            .output { timeoutMillis = 1_000 }
            .processInfo
            .exitCode

        assertEquals(expected, actual)
    }

    @Test
    fun givenExitCode_whenTerminated_thenIsSignalCode() {
        if (IsDarwinMobile || IsWindows) {
            skipping()
            return
        }

        val expected = Signal.SIGKILL
        val actual = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 1; exit 42")
            .destroySignal(expected)
            // Should be killed before completing via signal
            .output{ timeoutMillis = 250 }
            .processInfo
            .exitCode

        assertEquals(expected.code, actual)
    }

    @Test
    fun givenDestroy_whenSIGTERM_thenReturnsCorrectExitCode() = runTest {
        if (IsDarwinMobile) {
            skipping()
            return@runTest
        }

        val exitCode = try {
            Process.Builder(command = "sleep")
                .args("3")
                .destroySignal(Signal.SIGTERM)
                .spawn { process ->
                    process.waitForAsync(250.milliseconds, ::delay)
                    process
                    // destroy called on lambda closure
                }.waitForAsync(::delay)
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                skipping()
                return@runTest
            }
            throw e
        }

        assertEquals(Signal.SIGTERM.code, exitCode)
    }

    @Test
    fun givenDestroy_whenSIGKILL_thenReturnsCorrectExitCode() = runTest {
        if (IsDarwinMobile) {
            skipping()
            return@runTest
        }

        val exitCode = try {
            Process.Builder(command = "sleep")
                .args("3")
                .destroySignal(Signal.SIGKILL)
                .spawn { process ->
                    process.waitForAsync(250.milliseconds, ::delay)
                    process
                    // destroy called on lambda closure
                }.waitForAsync(::delay)
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                skipping()
                return@runTest
            }
            throw e
        }

        assertEquals(Signal.SIGKILL.code, exitCode)
    }

    @Test
    fun givenOutputFeeds_whenDestroyed_thenAreEjected() = runTest {
        if (IsDarwinMobile) {
            skipping()
            return@runTest
        }

        val process = try {
            Process.Builder(command = "sleep")
                .args("3")
                .destroySignal(Signal.SIGKILL)
                .spawn { process ->

                    process.stdoutFeed {}
                    process.stderrFeed {}

                    assertEquals(1, process.stdoutFeedsSize())
                    assertEquals(1, process.stderrFeedsSize())

                    val feed = OutputFeed {  }

                    process.stdoutFeed(
                        // Ensures that only added once
                        feed,
                        feed,
                        OutputFeed {  },
                    )
                    process.stderrFeed(
                        // Ensures that only added once
                        feed,
                        feed,
                        OutputFeed {  },
                        OutputFeed {  },
                    )

                    assertEquals(1 + 2, process.stdoutFeedsSize())
                    assertEquals(1 + 3, process.stderrFeedsSize())

                    process.waitForAsync(100.milliseconds, ::delay)

                    process
                    // destroy called on lambda closure
                }
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                skipping()
                return@runTest
            }
            throw e
        }

        process.waitForAsync(::delay)

        withContext(Dispatchers.Default) { delay(100.milliseconds) }

        assertEquals(0, process.stdoutFeedsSize())
        assertEquals(0, process.stderrFeedsSize())
    }

    @Test
    fun givenOutputFeeds_whenStdioNotPipe_thenAreNotAttached() = runTest {
        if (IsDarwinMobile) {
            skipping()
            return@runTest
        }

        try {
            Process.Builder(command = "sleep")
                .args("3")
                .destroySignal(Signal.SIGKILL)
                .stdout(Stdio.Inherit)
                .stderr(Stdio.Inherit)
                .spawn { p ->

                    p.stdoutFeed(
                        OutputFeed {  },
                        OutputFeed {  },
                    )

                    p.stderrFeed(
                        OutputFeed {  },
                        OutputFeed {  },
                        OutputFeed {  },
                    )

                    assertEquals(0, p.stdoutFeedsSize())
                    assertEquals(0, p.stderrFeedsSize())

                    p.waitForAsync(100.milliseconds, ::delay)
                }
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (IsWindows) {
                skipping()
                return@runTest
            }
            throw e
        }
    }

    private fun skipping() {
        println("Skipping...")
    }
}
