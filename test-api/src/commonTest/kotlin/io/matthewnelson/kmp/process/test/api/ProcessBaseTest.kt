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
@file:Suppress("PropertyName")

package io.matthewnelson.kmp.process.test.api

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDOUT
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.math.min
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("DEPRECATION", "UnusedReceiverParameter")
abstract class ProcessBaseTest {

    protected open val IsAndroidInstrumentTest: Boolean = false
    protected open val homeDir get() = LOADER.resourceDir
    protected open val cacheDir get() = homeDir.resolve("cache")
    protected open val dataDir get() = homeDir.resolve("data")

    protected open fun assertExitCode(code: Int) {
        val expected = if (IsWindows) Signal.SIGTERM.code else 0
        assertEquals(expected, code)
    }

    @Test
    open fun givenCurrentProcess_whenPid_thenSucceeds() {
        // Really just testing Jvm/Android Runtime
        assertTrue(Process.Current.pid() > 0)
    }

    @Test
    fun givenCurrentProcess_whenEnvironment_thenIsNotEmpty() {
        assertTrue(Process.Current.environment().isNotEmpty())
    }

    @Test
    fun givenStdin_whenFile_thenOutputIsAsExpected() {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return
        }

        val tempDir = SysTempDir.resolve("kmp_process")
        val testCat = tempDir.resolve("test.cat")

        testCat.delete()
        testCat.parentFile?.mkdirs()

        val expected = """
            abc
            123
            def
            456
        """.trimIndent()

        testCat.writeUtf8(expected)

        val out = Process.Builder(command = "cat")
            .args("-")
            .chdir(tempDir)
            .stdin(Stdio.File.of(testCat))
            .output()

        println(out.stdout)
        println(out.stderr)
        println(out)

        assertEquals(expected, out.stdout)
        assertEquals("", out.stderr)
        assertNull(out.processError)
        assertEquals(0, out.processInfo.exitCode)
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsStatusCode() {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
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
            println("Skipping...")
            return
        }

        val output = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 1; exit 42")
            .destroySignal(Signal.SIGKILL)
            // Should be killed before completing via signal
            .output{ timeoutMillis = 250 }

        val actual = output
            .processInfo
            .exitCode

        // Depending on Android API, destroySignal is modified
        // at build time to reflect the underlying kill signal
        // used when destroy is invoked.
        val expected = output
            .processInfo
            .destroySignal
            .code

        assertEquals(expected, actual)
    }

    @Test
    fun givenChdir_whenExpressed_thenChangesDirectories() {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return
        }

        val d = SysTempDir.resolve("try_chdir")
        d.delete()
        assertTrue(d.mkdirs())

        val output = Process.Builder(command = "sh")
            .args("-c")
            .args("echo \"$(pwd)\"; sleep 0.25; exit 0")
            .chdir(d)
            .stdin(Stdio.Null)
            .output { timeoutMillis = 500 }

        println(output.stdout)
        println(output.stderr)
        println(output)
        assertEquals(d.canonicalPath(), output.stdout)
    }

    @Test
    fun givenOutput_whenStderrOutput_thenIsAsExpected() {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return
        }

        val expected = "Hello World!"
        val out = Process.Builder(command = "sh")
            .args("-c")
            .args("echo 1>&2 \"$expected\"")
            .output()

        assertEquals("", out.stdout)
        assertEquals(expected, out.stderr)
    }

    @Test
    fun givenOutput_whenInput_thenStdoutIsAsExpected() {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return
        }

        val expected = buildString {
            repeat(50_000) { appendLine(it) }
            append("50000")
        }
        val out = Process.Builder(command = "cat")
            .args("-")
            // should be automatically set
            // to Pipe because there is input
            .stdin(Stdio.Inherit)
            .output {
                inputUtf8 { expected }
            }

        assertEquals(Stdio.Pipe, out.processInfo.stdio.stdin)
        @Suppress("ReplaceAssertBooleanWithAssertEquality")
        assertTrue(expected == out.stdout, "Stdout output did not match expected")
        assertEquals("", out.stderr)
    }

    @Test
    fun givenFeed_whenExceptionHandler_thenNotifies() = runTest(timeout = 5.seconds) {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val shouldThrow = !IsAndroidInstrumentTest && !IsNodeJs

        var invocationError = 0
        val p = try {
            Process.Builder(command = "sh")
                .args("-c")
                .args("echo \"something\"; sleep 1; exit 42")
                .onError { t ->
                    invocationError++
                    assertEquals(CTX_FEED_STDOUT, t.context)
                    assertIs<IllegalStateException>(t.cause)

                    // Node.js will crash (expected)
                    if (shouldThrow) throw t
                }
                .stdin(Stdio.Null)
                .stdout(Stdio.Pipe)
                .stderr(Stdio.Pipe)
                .spawn()
        } catch (e: IOException) {
            if (IsDarwinMobile) {
                println("Skipping...")
                return@runTest
            }
            throw e
        }

        currentCoroutineContext().job.invokeOnCompletion { p.destroy() }

        p.stdoutFeed { line ->
            throw IllegalStateException(line)
        }.waitForAsync(500.milliseconds)

        delayTest(100.milliseconds)

        assertTrue(invocationError > 0)

        if (!shouldThrow) {
            assertTrue(p.isAlive)
            p.destroy()
        } else {
            // non-Node.js process should have
            // been destroyed upon onOutput throwing
            // exception.
            assertFalse(p.isAlive)
        }

        // Ensure waiters work properly for all platforms, indicating that
        // OutputFeed.Handler closed up shop for all feeds (especially native).
        // If it is not correct, this would suspend until test timeout occurred
        p.stderrWaiter().awaitStopAsync().stdoutWaiter().awaitStopAsync()
    }

    @Test
    fun givenSpawn_whenInput_thenStdoutIsAsExpected() = runTest {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val size = 50000
        val expected = ArrayList<String>(size).apply {
            repeat(size) { i -> add(i.toString()) }
        }
        val actual = ArrayList<String>(size * 2)

        val exitCode = Process.Builder(command = "cat")
            .args("-")
            .spawn { p ->
                val data = expected
                    .joinToString("\n", postfix = "\n")
                    .encodeToByteArray()

                p.stdoutFeed { line ->
                    if (line == null) return@stdoutFeed
                    actual.add(line)
                }

                var offset = 0
                // chunked
                while (offset < data.size) {
                    val len = min(4097, data.size - offset)
                    p.input!!.writeAsync(data, offset, len)
                    offset += len
                }

                p.input!!.writeAsync(data)

                p.input!!.close()

                delayTest(250.milliseconds)

                p
            }
            .stdoutWaiter()
            .awaitStopAsync()
            .waitForAsync()

        assertEquals(0, exitCode)
        assertEquals(expected.size * 2, actual.size)

        val expectedLen = (expected + expected).let { var count = 0; it.forEach { line -> count += line.length }; count }
        val actualLen = actual.let { var count = 0; it.forEach { line -> count += line.length }; count }
        assertEquals(expectedLen, actualLen)
    }

    @Test
    fun givenStderrFile_whenSameAsStdout_thenStderrRedirectedToStdout() = runTest {
        if (IsDarwinMobile || IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val f = SysTempDir
            .resolve("kmp_process_redirect")
            .resolve("stdout_stderr.txt")

        if (f.exists() && !f.delete()) {
            fail("Failed to delete test file[$f]")
        }

        Process.Builder(command = "sh")
            .args("-c")
            .args("echo \"stdout\"; echo 1>&2 \"stderr\"")
            .stdin(Stdio.Null)
            .stdout(Stdio.File.of(f))
            .stderr(Stdio.File.of(f))
            .spawn { p ->
                p.waitForAsync()

                delayTest(250.milliseconds)
            }

        val lines = f.readUtf8().lines()
        assertEquals(3, lines.size)
        assertEquals("stdout", lines[0])
        assertEquals("stderr", lines[1])
    }

    @Test
    open fun givenExecutable_whenOutputToFile_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        val logsDir = homeDir.resolve("logs")
        val stdoutFile = logsDir.resolve("tor.log")
        val stderrFile = logsDir.resolve("tor.err")

        stdoutFile.delete()
        stderrFile.delete()
        logsDir.delete()

        assertFalse(stdoutFile.exists())
        assertFalse(stderrFile.exists())

        LOADER.toProcessBuilder()
            .stdout(Stdio.File.of(stdoutFile, append = true))
            .stderr(Stdio.File.of(stderrFile))
            .spawn { p ->
                println(p)

                withContext(Dispatchers.Default) {
                    p.waitForAsync(100.milliseconds)
                }

                // parent dir was created by Stdio.Config.Builder.build
                assertTrue(stdoutFile.exists())
                assertTrue(stderrFile.exists())

                withContext(Dispatchers.Default) {
                    p.waitForAsync(2.seconds)
                }
            }

        delayTest(250.milliseconds)

        stdoutFile.readUtf8().assertTorRan()
    }

    @Test
    open fun givenExecutable_whenOutput_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        val out = LOADER.toProcessBuilder()
            .output { timeoutMillis = 2_000 }

        println(out)
        println(out.stdout)
        println(out.stderr)

        delayTest(250.milliseconds)

        assertExitCode(out.processInfo.exitCode)
        out.stdout.assertTorRan()
    }

    @Test
    open fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        LOADER.toProcessBuilder().spawn { p ->
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            p.stdoutFeed { line ->
                if (line == null) return@stdoutFeed
                with(stdoutBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }.stderrFeed { line ->
                if (line == null) return@stderrFeed
                with(stderrBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }

            assertFailsWith<IllegalStateException> {
                p.stdoutWaiter()
            }
            assertFailsWith<IllegalStateException> {
                p.stderrWaiter()
            }

            withContext(Dispatchers.Default) {
                p.waitForAsync(2.seconds)
            }

            val exitCode = p.destroy()
                .stdoutWaiter()
                .awaitStopAsync()
                .stderrWaiter()
                .awaitStopAsync()
                .waitForAsync()

            val stdoutString = stdoutBuilder.toString()
            val stderrString = stderrBuilder.toString()
            println(stdoutString)
            println(stderrString)

            assertExitCode(exitCode)
            stdoutString.assertTorRan()
        }

        delayTest(250.milliseconds)
    }

    private fun String.assertTorRan() {
        var ran = false
        lines().forEach { line ->
            if (line.contains("[notice] Tor")) {
                ran = true
            }
        }

        assertTrue(ran)
    }

    private suspend fun TestScope.delayTest(duration: Duration) {
        withContext(Dispatchers.Default) { delay(duration) }
    }

    private fun Process.Builder.envHome(): Process.Builder = environment("HOME", homeDir.path)

    private fun ResourceLoader.Tor.Exec.toProcessBuilder(): Process.Builder {
        val geoipFiles = extract()

        val b = process(TorResourceBinder) { tor, configureEnv ->
            Process.Builder(executable = tor)
                .args("--DataDirectory")
                .args(dataDir.also { it.mkdirs() }.path)
                .args("--CacheDirectory")
                .args(cacheDir.also { it.mkdirs() }.path)
                .args("--GeoIPFile")
                .args(geoipFiles.geoip.path)
                .args("--GeoIPv6File")
                .args(geoipFiles.geoip6.path)
                .args("--DormantCanceledByStartup")
                .args("1")
                .args("--SocksPort")
                .args("auto")
                .args("--DisableNetwork")
                .args("1")
                .args("--RunAsDaemon")
                .args("0")
                .args("--__OwningControllerProcess")
                .args(Process.Current.pid().toString())
                .destroySignal(Signal.SIGTERM)
                .envHome()
                .environment(configureEnv)
                .stdin(Stdio.Null)
                .stdout(Stdio.Pipe)
                .stderr(Stdio.Pipe)
        }

        if (!IsDarwinMobile) {
            b.chdir(homeDir)
        }

        return b
    }
}
