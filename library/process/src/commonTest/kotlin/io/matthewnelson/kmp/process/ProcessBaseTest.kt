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
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.isWindows
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private val installer = TorResources(installationDir = SysTempDir.resolve("kmp_process"))
    }

    protected abstract val isDarwinMobile: Boolean
    protected abstract val isJvm: Boolean
    protected abstract val isNodeJS: Boolean
    protected abstract val isUnixDesktop: Boolean

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
                    .destroySignal(Signal.SIGKILL)
                    .spawn()
            } catch (e: IOException) {
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
                p.destroy()
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
            .destroySignal(Signal.SIGKILL)
            .spawn()

        try {
            assertEquals(expected, p.waitFor(1.seconds))
        } finally {
            p.destroy()
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
                .destroySignal(Signal.SIGKILL)
                .spawn()
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (!isUnixDesktop) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        destroyOnCompletion(p)

        assertEquals(0, p.waitFor())
    }

    @Test
    fun givenDestroy_whenSigTERMorKILL_thenReturnsCorrectExitCode() = runTest {
        if (isDarwinMobile) {
            println("Skipping...")
            return@runTest
        }

        val (pTerm, pKill) = try {
            val b = Process.Builder("sleep")
                .args("3")

            Pair(b.spawn(), b.destroySignal(Signal.SIGKILL).spawn())
        } catch (e: IOException) {
            // Host (Window) did not have sleep available
            if (!isUnixDesktop) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        destroyOnCompletion(pTerm)
        destroyOnCompletion(pKill)

        withContext(Dispatchers.Default) { delay(250.milliseconds) }

        pTerm.destroy()
        pKill.destroy()

        withContext(Dispatchers.Default) { delay(250.milliseconds) }

        // TODO: Fix Native exitCode
        if (isJvm || isNodeJS) {
            assertEquals(Signal.SIGTERM.code, pTerm.exitCode())
            assertEquals(Signal.SIGKILL.code, pKill.exitCode())
        } else {
            println("TERM: ${pTerm.exitCode()}")
            println("KILL: ${pKill.exitCode()}")
        }
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
                .destroySignal(Signal.SIGKILL)
                .spawn()
        } catch (e: IOException) {
            // Host (Window or iOS) did not have sleep available
            if (!isUnixDesktop) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        destroyOnCompletion(p)

        val exitCode = withContext(Dispatchers.Default) {
            p.waitForAsync(::delay)
        }

        assertEquals(0, exitCode)
    }

    @Test
    fun givenExecutableFile_whenExecuteAsProcess_thenIsSuccessful() = runTest(timeout = 45.seconds) {
        val paths = installer.install()

        val stdout = installer.installationDir.resolve("tor.log")
        val stderr = installer.installationDir.resolve("tor.err")

        stdout.delete()
        stderr.delete()

        val b = Process.Builder(paths.tor)
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
            .stdin(Stdio.Null)
            .stdout(Stdio.File.of(stdout))
            .stderr(Stdio.File.of(stderr))

        val p = b.spawn()

        destroyOnCompletion(p)

        println(p)

        // Should not attach b/c using Stdio.File
        p.stdoutFeed {}
        p.stderrFeed {}
        assertEquals(0, p.stdoutFeedsSize())
        assertEquals(0, p.stderrFeedsSize())

        assertTrue(stdout.exists())
        assertTrue(stderr.exists())

        withContext(Dispatchers.Default) {
            p.waitForAsync(5.seconds, ::delay)
        }

        p.destroy()

        withContext(Dispatchers.Default) { delay(250.milliseconds) }

        assertFalse(p.isAlive)

        // tor should have handled SIGTERM gracefully
        val expected = when {
            isWindows -> when {
                isJvm || isNodeJS -> Signal.SIGTERM.code
                else -> 0
            }
            else -> 0
        }
        assertEquals(expected, p.exitCode())

        assertTrue(stdout.readUtf8().lines().first().contains(" [notice] Tor "))
        assertTrue(stderr.readUtf8().isEmpty())

        val out = b.output { timeoutMillis = 5_000 }

        assertEquals(expected, out.processInfo.exitCode)
        assertTrue(out.stdout.lines().first().contains(" [notice] Tor "))
        assertTrue(out.stderr.isEmpty())

        println(out)

        b.stdout(Stdio.Pipe).stderr(Stdio.Pipe).spawn().let { p2 ->
            destroyOnCompletion(p2)

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            p2.stdoutFeed { line ->
                with(stdoutBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }.stderrFeed { line ->
                with(stderrBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }

            assertEquals(1, p2.stdoutFeedsSize())
            assertEquals(1, p2.stderrFeedsSize())

            withContext(Dispatchers.Default) {
                p2.waitForAsync(2.seconds, ::delay)
            }

            p2.destroy()

            val stdoutString = stdoutBuilder.toString()
            val stderrString = stderrBuilder.toString()
            println(stdoutString)
            println(stderrString)

            withContext(Dispatchers.Default) { delay(250.milliseconds) }

            assertEquals(0, p2.stdoutFeedsSize())
            assertEquals(0, p2.stderrFeedsSize())
            assertEquals(expected, p2.exitCode())
            assertTrue(stdoutString.lines().first().contains(" [notice] Tor "))
            assertTrue(stderrString.isEmpty())
        }
    }

    protected fun TestScope.destroyOnCompletion(p: Process) {
        coroutineContext.job.invokeOnCompletion { p.destroy() }
    }
}
