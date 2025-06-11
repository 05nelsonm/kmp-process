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

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.internal.IsWindows
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class ProcessUnitTest {

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
                .useSpawn { process ->
                    process.waitForAsync(250.milliseconds)
                    process
                    // destroy called on lambda closure
                }.waitForAsync()
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
                .useSpawn { process ->
                    process.waitForAsync(250.milliseconds)
                    process
                    // destroy called on lambda closure
                }.waitForAsync()
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
                .useSpawn { process ->

                    process.stdoutFeed {}
                    process.stderrFeed {}

                    assertEquals(1, process.stdoutFeedsSize())
                    assertEquals(1, process.stderrFeedsSize())

                    val feed = OutputFeed { }

                    process.stdoutFeed(
                        // Ensures that only added once
                        feed,
                        feed,
                        OutputFeed { },
                    )
                    process.stderrFeed(
                        // Ensures that only added once
                        feed,
                        feed,
                        OutputFeed { },
                        OutputFeed { },
                    )

                    assertEquals(1 + 2, process.stdoutFeedsSize())
                    assertEquals(1 + 3, process.stderrFeedsSize())

                    process.waitForAsync(100.milliseconds)

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

        process
            .stdoutWaiter()
            .awaitStopAsync()
            .stderrWaiter()
            .awaitStopAsync()

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
                .useSpawn { p ->

                    p.stdoutFeed(
                        OutputFeed { },
                        OutputFeed { },
                    )

                    p.stderrFeed(
                        OutputFeed { },
                        OutputFeed { },
                        OutputFeed { },
                    )

                    assertEquals(0, p.stdoutFeedsSize())
                    assertEquals(0, p.stderrFeedsSize())

                    p.waitForAsync(100.milliseconds)
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

    internal companion object {
        internal fun skipping() {
            println("Skipping...")
        }
    }
}
