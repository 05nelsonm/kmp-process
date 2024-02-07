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
package io.matthewnelson.process

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

abstract class ProcessBaseTest {

    private companion object {
        private val installer = TorResources(installationDir = SysTempDir.resolve("process"))
    }

    protected abstract val isUnixDesktop: Boolean
    protected abstract val isNodeJS: Boolean
    protected abstract val isDarwinMobile: Boolean

    @Test
    fun givenWaitFor_whenProcessExits_thenWaitForReturnsEarly() {
        if (isNodeJS || isDarwinMobile) {
            println("Skipping...")
            return
        }

        val runTime = measureTime {
            val p = try {
                Process.Builder("sleep")
                    .args("0.25")
                    .start()
            } catch (e: ProcessException) {
                // Host (Window or iOS) did not have sleep available
                if (!isUnixDesktop) {
                    println("Skipping...")
                    return
                }
                throw e
            }

            try {
                assertNull(p.waitFor(100.milliseconds))
                assertTrue(p.isAlive)
                assertEquals(0, p.waitFor(2.seconds))
                assertFalse(p.isAlive)
            } finally {
                p.sigkill()
            }
        }

        // Should be less than the 2 seconds (dropped out early)
        assertTrue(runTime < 1.seconds)
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsAsExpected() {
        if (!isUnixDesktop || isNodeJS) {
            println("Skipping...")
            return
        }

        val expected = 42
        val p = Process.Builder("sh")
            .args("-c")
            .args("sleep 0.25; exit $expected")
            .start()

        try {
            assertEquals(expected, p.waitFor(1.seconds))
        } finally {
            p.sigkill()
        }
    }

    @Test
    fun givenWaitFor_whenCompletion_thenReturnsExitCode() = runTest {
        if (isNodeJS || isDarwinMobile) {
            println("Skipping...")
            return@runTest
        }

        val p = try {
            Process.Builder("sleep")
                .args("1")
                .start()
        } catch (e: ProcessException) {
            // Host (Window) did not have sleep available
            if (!isUnixDesktop) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        sigkillOnCompletion(p)

        assertEquals(0, p.waitFor())
    }

    @Test
    fun givenWaitForAsync_whenCompletion_thenReturnsExitCode() = runTest {
        if (isDarwinMobile) {
            println("Skipping...")
            return@runTest
        }

        val p = try {
            Process.Builder("sleep")
                .args("1")
                .start()
        } catch (e: ProcessException) {
            // Host (Window or iOS) did not have sleep available
            if (!isUnixDesktop) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        sigkillOnCompletion(p)

        val exitCode = withContext(Dispatchers.Default) {
            p.waitForAsync()
        }

        assertEquals(0, exitCode)
    }

    @Test
    fun givenExecutableFile_whenExecuteAsProcess_thenIsSuccessful() = runTest(timeout = 25.seconds) {
        val paths = installer.install()

        val p = Process.Builder(paths.tor.path)
            .args("--DataDirectory")
            .args(installer.installationDir.resolve("data").path)
            .args("--CacheDirectory")
            .args(installer.installationDir.resolve("cache").path)
            .args("--GeoIPFile")
            .args(paths.geoip.path)
            .args("--GeoIPv6File")
            .args(paths.geoip6.path)
            .args("--DormantCanceledByStartup")
            .args("1")
            .args("--ControlPort")
            .args("auto")
            .args("--SocksPort")
            .args("auto")
            .args("--DisableNetwork")
            .args("1")
            .args("--RunAsDaemon")
            .args("0")
            .environment("HOME", installer.installationDir.path)
            .stdout(Stdio.Inherit)
            .stderr(Stdio.Inherit)
            .start()

        sigkillOnCompletion(p)

        println("CMD[${p.command}]")
        p.args.forEach { arg -> println("ARG[$arg]") }

        try {
            withContext(Dispatchers.Default) {
                p.waitForAsync(5.seconds)
            }
        } finally {
            p.sigterm()
        }
    }

    protected fun TestScope.sigkillOnCompletion(p: Process) {
        coroutineContext.job.invokeOnCompletion { p.sigkill() }
    }
}
