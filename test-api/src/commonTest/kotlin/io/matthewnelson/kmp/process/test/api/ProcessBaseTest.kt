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
@file:Suppress("PropertyName", "UnusedReceiverParameter")

package io.matthewnelson.kmp.process.test.api

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Output.Data.Companion.consolidate
import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDOUT
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
    fun givenStdin_whenFile_thenOutputIsAsExpected() = runTest {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val tempDir = SysTempDir.resolve("kmp_process")
        val testCat = tempDir.resolve("test.cat")

        testCat.delete2()
        testCat.parentFile?.mkdirs2(mode = null)

        val expected = """
            abc
            123
            def
            456
        """.trimIndent()

        testCat.writeUtf8(excl = null, expected)

        fun Output.assertOutput() {
            try {
                assertEquals(expected, stdoutBuf.utf8())
                assertEquals("", stderrBuf.utf8())
                assertNull(processError)
                assertEquals(0, processInfo.exitCode)
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        try {
            val b = Process.Builder(command = if (IsAppleSimulator) "/bin/cat" else "cat")
                .args("-")
                .changeDirectory(if (IsAppleSimulator) null else tempDir)
                .stdin(Stdio.File.of(testCat))

            b.createOutput().assertOutput()
            b.createOutputAsync().assertOutput()
        } finally {
            testCat.delete2()
        }
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsStatusCode() = runTest {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val expected = 42

        val b = Process.Builder(command = if (IsAppleSimulator) "/bin/sh" else "sh")
            .args("-c")
            .args("sleep 0.25; exit $expected")
            .destroySignal(Signal.SIGKILL)

        // Should complete and exit before timing out
        b.createOutput { timeoutMillis = 1.seconds.inWholeMilliseconds.toInt() }
            .processInfo
            .exitCode
            .let { assertEquals(expected, it, "output") }

        // Should complete and exit before timing out
        b.createOutputAsync { timeoutMillis = 1.seconds.inWholeMilliseconds.toInt() }
            .processInfo
            .exitCode
            .let { assertEquals(expected, it, "outputAsync") }
    }

    @Test
    fun givenExitCode_whenTerminated_thenIsSignalCode() = runTest {
        if (IsAppleSimulator || IsWindows) {
            println("Skipping...")
            return@runTest
        }

        fun Output.assertOutput() {
            // Depending on Android API, destroySignal is modified
            // at build time to reflect the underlying kill signal
            // used when destroy is invoked.
            val expected = processInfo.destroySignal.code
            assertEquals(expected, processInfo.exitCode)
        }

        val b = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 2; exit 42")
            .destroySignal(Signal.SIGKILL)
            // Should be killed before completing via signal

        b.createOutput{ timeoutMillis = 250 }.assertOutput()
        b.createOutputAsync { timeoutMillis = 250 }.assertOutput()
    }

    @Test
    fun givenChdir_whenExpressed_thenChangesDirectories() = runTest {
        if (IsAppleSimulator || IsWindows) {
            // no chdir on apple simulator
            println("Skipping...")
            return@runTest
        }

        val d = SysTempDir
            .resolve("try_chdir")
            .mkdirs2(mode = null)

        val expected = d.canonicalPath2() + '\n' // echo appends a new line character

        fun Output.assertOutput() {
            try {
                assertEquals(expected, stdoutBuf.utf8())
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        val b = Process.Builder(command = "sh")
            .args("-c")
            .args("echo \"$(pwd)\"; sleep 0.25; exit 0")
            .changeDirectory(d)
            .stdin(Stdio.Null)

        b.createOutput { timeoutMillis = 500 }.assertOutput()
        b.createOutputAsync { timeoutMillis = 500 }.assertOutput()
    }

    @Test
    fun givenCommand_whenInvalid_thenThrowsExceptionOnSpawn() = runTest {
        val dir1 = SysTempDir.resolve(Random.nextBytes(8).toHexString())
        val dir2 = dir1.resolve("path")
        val invalid = dir2.resolve("not_a_program").delete2(ignoreReadOnly = true)

        // Absolute path
        var b = Process.Builder(command = invalid.path)
        assertFailsWith<FileNotFoundException> { b.createOutput() }
        assertFailsWith<FileNotFoundException> { b.createOutputAsync() }
        assertFailsWith<FileNotFoundException> { b.createProcessAsync().destroy() }

        fun Throwable.assertRelativePathException() = when(this) {
            is FileNotFoundException -> {} // pass
            // Android may error out with EPERM/EACCES b/c cwd is /
            is AccessDeniedException -> {} // pass
            is IOException -> if (AndroidNativeDeviceAPILevel?.let { it >= 28 } == true) {
                // AndroidNative posix_spawnp may not fail with ENOENT, but fail
                // on its exec step. As a result, the spawnFailureToIOException
                // will not be able to deduce the proper exception type because
                // of the command is not an absolute path.
                //
                // pass
            } else {
                fail("!(AndroidNativeDeviceAPILevel >= 28) && is IOException", this)
            }
            else -> throw this
        }

        // Relative path
        b = Process.Builder(command = invalid.name)
        try {
            b.createOutput()
            fail("Process.Builder.createOutput should have thrown an IOException")
        } catch (t: Throwable) {
            t.assertRelativePathException()
        }
        try {
            b.createOutputAsync()
            fail("Process.Builder.createOutputAsync should have thrown an IOException")
        } catch (t: Throwable) {
            t.assertRelativePathException()
        }
        try {
            b.createProcessAsync().destroy()
            fail("Process.Builder.createProcessAsync should have thrown an IOException")
        } catch (t: Throwable) {
            t.assertRelativePathException()
        }

        fun Throwable.assertPermissionsException() = when(this) {
            is FileNotFoundException -> if (IsWindows) {
                // Windows has no concept of file permissions, so may instead
                // fail with ENOENT b/c the file is not an executable with a
                // main function.
                //
                // pass
            } else {
                fail("!IsWindows && is FileNotFoundException", this)
            }
            is AccessDeniedException -> {} // pass
            is IOException -> if (IsWindows) {
                // Windows may also fail due to it being an invalid program on Jvm (error 193)
                //
                // pass
            } else {
                fail("!IsWindows && is IOException", this)
            }
            else -> throw this
        }

        // Invalid permissions
        b = Process.Builder(command = invalid.path)
        try {
            dir2.mkdirs2(mode = null, mustCreate = true)
            invalid.writeUtf8(excl = OpenExcl.MustCreate.of("666"), "Non-Executable")
            assertTrue(invalid.exists2())

            try {
                b.createOutput()
                fail("Process.Builder.createOutput should have thrown an IOException")
            } catch (t: Throwable) {
                t.assertPermissionsException()
            }
            try {
                b.createOutputAsync()
                fail("Process.Builder.createOutputAsync should have thrown an IOException")
            } catch (t: Throwable) {
                t.assertPermissionsException()
            }
            try {
                b.createProcessAsync().destroy()
                fail("Process.Builder.createProcessAsync should have thrown an IOException")
            } catch (t: Throwable) {
                t.assertPermissionsException()
            }
        } finally {
            invalid.delete2()
            dir2.delete2()
            dir1.delete2()
        }
    }

    @Test
    fun givenOutput_whenStderrOutput_thenIsAsExpected() = runTest {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val echo = "Hello World!"
        val expected = "$echo\n" // echo appends a new line character

        fun Output.assertOutput() {
            try {
                assertEquals("", stdoutBuf.utf8())
                assertEquals(expected, stderrBuf.utf8())
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        val b = Process.Builder(command = if (IsAppleSimulator) "/bin/sh" else "sh")
            .args("-c")
            .args("echo 1>&2 \"$echo\"")

        b.createOutput().assertOutput()
        b.createOutputAsync().assertOutput()
    }

    @Test
    fun givenOutput_whenInput_thenStdoutIsAsExpected() = runTest {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val expected = buildString {
            repeat(100_000) { appendLine(it) }
            append("100000")
        }

        @Suppress("ReplaceAssertBooleanWithAssertEquality")
        fun Output.assertOutput() {
            try {
                assertNull(processError)
                assertEquals(Stdio.Pipe, processInfo.stdio.stdin)
                assertTrue(
                    expected == stdoutBuf.utf8(),
                    "STDOUT did not match expected >> actual.length[${stdoutBuf.utf8().length}] vs expected.length[${expected.length}]"
                )
                assertEquals("", stderrBuf.utf8())
            } catch (t: AssertionError) {
//                println(stdout)
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        val b = Process.Builder(command = if (IsAppleSimulator) "/bin/cat" else "cat")
            .args("-")
            // should be automatically set
            // to Pipe because there is input
            .stdin(Stdio.Inherit)

        b.createOutput {
            inputUtf8 { expected }
            timeoutMillis = 5.seconds.inWholeMilliseconds.toInt()
            maxBuffer = Int.MAX_VALUE / 2
        }.assertOutput()

        b.createOutputAsync {
            inputUtf8 { expected }
            timeoutMillis = 5.seconds.inWholeMilliseconds.toInt()
            maxBuffer = Int.MAX_VALUE / 2
        }.assertOutput()
    }

    @Test
    fun givenOutput_whenNoOutput_thenReturnsBeforeTimeout() = runTest {
        if (IsAppleSimulator || IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val expectedExitCode = 42
        val sleepSeconds = 1

        fun Output.assertOutput(mark: TimeSource.Monotonic.ValueTimeMark) {
            val elapsed = mark.elapsedNow()
            println("elapsed[${elapsed.inWholeMilliseconds}ms]")

            try {
                assertNull(processError, "processError != null")
                assertEquals(expectedExitCode, processInfo.exitCode, "code[${processInfo.exitCode}]")
                assertTrue(stdoutBuf.utf8().isEmpty(), "stdout was not empty")
                assertTrue(stderrBuf.utf8().isEmpty(), "stderr was not empty")
                val min = sleepSeconds.seconds
                // Only need to ensure that the process ended early, before the 10s timeout.
                // Native/Linux & Native/Android are sometimes incredibly slow to spawn because
                // of how the posix_spawn implementation closes file descriptors.
                val max = min + 3.seconds
                assertTrue(
                    elapsed in min..max,
                    "elapsed[${elapsed.inWholeMilliseconds}ms] !in min[${min.inWholeMilliseconds}ms]..max[${max.inWholeMilliseconds}ms]"
                )
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        val b = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep $sleepSeconds; exit $expectedExitCode")

        var mark = TimeSource.Monotonic.markNow()
        b.createOutput { timeoutMillis = 10.seconds.inWholeMilliseconds.toInt() }.assertOutput(mark)
        mark = TimeSource.Monotonic.markNow()
        b.createOutputAsync { timeoutMillis = 10.seconds.inWholeMilliseconds.toInt() }.assertOutput(mark)
    }

    @Test
    fun givenFeed_whenExceptionHandler_thenNotifies() = runTest(timeout = 5.seconds) {
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val shouldThrow = !IsAndroidInstrumentTest && !IsNodeJs

        var p: Process? = null
        currentCoroutineContext().job.invokeOnCompletion { p?.destroy() }

        var invocationError = 0
        p = Process.Builder(command = if (IsAppleSimulator) "/bin/sh" else "sh")
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
            .createProcessAsync()

        p.stdout(OutputFeed { line ->
            throw IllegalStateException(line)
        }).waitForAsync(500.milliseconds)

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
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val size = 50000
        val expected = ArrayList<String>(size).apply {
            repeat(size) { i -> add(i.toString()) }
        }
        val actual = ArrayList<String>(size * 2)

        val exitCode = Process.Builder(command = if (IsAppleSimulator) "/bin/cat" else "cat")
            .args("-")
            .createProcessAsync().use { p ->
                val data = expected
                    .joinToString("\n", postfix = "\n")
                    .encodeToByteArray()

                p.stdout(OutputFeed { line ->
                    if (line == null) return@OutputFeed
                    actual.add(line)
                })

                var offset = 0
                // chunked
                while (offset < data.size) {
                    val len = min(4097, data.size - offset)
                    p.input!!.writeAsync(data, offset, len)
                    offset += len
                }

                p.input!!.writeAsync(data)

                p.input!!.close()

                delayTest(500.milliseconds)

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
        if (IsWindows) {
            println("Skipping...")
            return@runTest
        }

        val f = SysTempDir
            .resolve("kmp_process_redirect")
            .resolve("stdout_stderr.txt")
            .delete2()

        Process.Builder(command = if (IsAppleSimulator) "/bin/sh" else "sh")
            .args("-c")
            .args("echo \"stdout\"; echo 1>&2 \"stderr\"")
            .stdin(Stdio.Null)
            .stdout(Stdio.File.of(f))
            .stderr(Stdio.File.of(f))
            .createProcessAsync().use { p ->
                p.waitForAsync()

                delayTest(250.milliseconds)
            }

        val lines = f.readUtf8().lines()
        assertEquals(3, lines.size)
        assertEquals("stdout", lines[0])
        assertEquals("stderr", lines[1])
    }

    @Test
    open fun givenExecutable_whenRelativePathWithChDir_thenExecutes() = runTest {
        if (IsAppleSimulator) {
            // chdir not supported
            println("Skipping...")
            return@runTest
        }

        fun Output.assertOutput() {
            try {
                assertTrue(stdoutBuf.utf8().startsWith("Tor version "))
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        val b = LOADER.process(TorResourceBinder) { tor, configureEnv ->
            val parentDirName = tor.parentPath?.substringAfterLast(SysDirSep)
            assertNotNull(parentDirName)

            val command = "..".toFile()
                .resolve(parentDirName)
                .resolve(tor.name)

            Process.Builder(command = command.path)
                .args("--version")
                .changeDirectory(tor.parentFile)
                .environment(configureEnv)
        }

        b.createOutput { timeoutMillis = 2_000 }.assertOutput()
        b.createOutputAsync { timeoutMillis = 2_000 }.assertOutput()
    }

    @Test
    open fun givenExecutable_whenOutputToFile_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        val logsDir = homeDir.resolve("logs")
        val stdoutFile = logsDir.resolve("tor.log")
        val stderrFile = logsDir.resolve("tor.err")

        stdoutFile.delete2()
        stderrFile.delete2()
        logsDir.delete2()

        LOADER.toProcessBuilder()
            .stdout(Stdio.File.of(stdoutFile, append = true))
            .stderr(Stdio.File.of(stderrFile))
            .createProcessAsync().use { p ->
                println(p)

                withContext(Dispatchers.Default) {
                    p.waitForAsync(100.milliseconds)
                }

                // parent dir was created by Stdio.Config.Builder.build
                assertTrue(stdoutFile.exists2())
                assertTrue(stderrFile.exists2())

                withContext(Dispatchers.Default) {
                    p.waitForAsync(2.seconds)
                }
            }

        delayTest(250.milliseconds)

        stdoutFile.readUtf8().assertTorRan()
    }

    @Test
    open fun givenExecutable_whenOutput_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        fun Output.assertOutput() {
            try {
                assertExitCode(processInfo.exitCode)
                stdoutBuf.utf8().assertTorRan()
            } catch (t: AssertionError) {
                println(stdoutBuf.utf8())
                println(stderrBuf.utf8())
                println(this)
                throw t
            }
        }

        delayTest(500.milliseconds)
        val b = LOADER.toProcessBuilder()
        b.createOutput { timeoutMillis = 3_000 }.assertOutput()
        b.createOutputAsync { timeoutMillis = 3_000 }.assertOutput()
    }

    @Test
    open fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected() = runTest(timeout = 25.seconds) {
        LOADER.toProcessBuilder().createProcessAsync().use { p ->
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            p.stdout(OutputFeed { line ->
                if (line == null) return@OutputFeed
                with(stdoutBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }).stderr(OutputFeed { line ->
                if (line == null) return@OutputFeed
                with(stderrBuilder) {
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            })

            assertFailsWith<IllegalStateException> { p.stdoutWaiter() }
            assertFailsWith<IllegalStateException> { p.stderrWaiter() }

            withContext(Dispatchers.Default) {
                p.waitForAsync(3.seconds)
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

    @Test
    open fun givenExecutable_whenPipeOutputFeeds_thenOutputFeedRawReceivesSameData() = runTest(timeout = 25.seconds) {
        LOADER.toProcessBuilder().createProcessAsync().use { p ->
            val stdoutData1 = mutableListOf<Output.Data?>()
            val stdoutData2 = mutableListOf<Output.Data?>()
            val stderrData = mutableListOf<Output.Data?>()
            p.stdout(
                OutputFeed.Raw { data -> stdoutData1.add(data) },
                OutputFeed.Raw { data -> stdoutData2.add(data) },
            ).stderr(
                OutputFeed.Raw { data -> stderrData.add(data) }
            )

            withContext(Dispatchers.Default) {
                p.waitForAsync(3.seconds)
            }

            val exitCode = p.destroy()
                .stdoutWaiter()
                .awaitStopAsync()
                .stderrWaiter()
                .awaitStopAsync()
                .waitForAsync()

            // null + 2 or more Output.Data instances.
            assertTrue(stdoutData1.size > 1 + 2)
            assertTrue(stdoutData1.contains(null), "!contains(null)")

            // Both Raw instances received the SAME Output.Data instance.
            assertEquals(stdoutData1, stdoutData2)

            // Same instances, hash code should equal the same (i.e. the same backing arrays in the same order)
            val stdoutConsolidated = stdoutData1.consolidate()
            assertEquals(stdoutConsolidated, stdoutData2.consolidate())

            val stdoutString = stdoutConsolidated.utf8()
            val stderrString = stderrData.consolidate().utf8()
            println(stdoutString)
            println(stderrString)

            assertExitCode(exitCode)
            stdoutString.assertTorRan()
        }

        delayTest(250.milliseconds)
    }

    private fun String.assertTorRan() {
        val expected = "[notice] Tor"
        lines().forEach { line ->
            if (line.contains(expected)) return
        }
        fail("Output does not contain expected >> $expected")
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
                .args(dataDir.mkdirs2(mode = "700").path)
                .args("--CacheDirectory")
                .args(cacheDir.mkdirs2(mode = "700").path)
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

        if (!IsAppleSimulator) b.changeDirectory(homeDir)

        return b
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Process.Builder.changeDirectory(dir: File?): Process.Builder {
        // TODO: Move to expect/actual and use changeDir extension
        return chdir(dir)
    }
}
