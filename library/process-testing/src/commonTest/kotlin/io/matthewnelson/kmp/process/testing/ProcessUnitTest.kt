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
package io.matthewnelson.kmp.process.testing

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.readUtf8
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

open class ProcessUnitTest {

    private companion object {
        // This is OK for Android Runtime, as only geoip files will be installed
        // to the Context.cacheDir/kmp_process. libtor.so is extracted to the
        // Context.applicationInfo.nativeLibraryDir automatically, so.
        private val installer by lazy {
            TorResources(installationDir = SysTempDir.resolve("kmp_process"))
        }
    }

    protected open val androidSdkInt: Int? = null

    private val isAndroidRuntime: Boolean get() = androidSdkInt != null

    // Android libtor.so files are compiled with minSdk support of 21, so
    // will throw exception in AndroidRuntime if testing on emulator below that
    private val canRunTor: Boolean get() = androidSdkInt?.let { it >= 21 } ?: true

    // TODO: Issue #50
    //  Remove hasRedirect
    private val hasRedirect: Boolean get() = androidSdkInt?.let { it >= 24 } ?: true

    protected open val homeDir by lazy { installer.installationDir }
    protected open val cacheDir by lazy { homeDir.resolve("cache") }
    protected open val dataDir by lazy { homeDir.resolve("data") }

    private val expectedTorExitCode = when {
        IsWindows -> Signal.SIGTERM.code
        isAndroidRuntime -> 1
        else -> 0
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
    fun givenExecutable_whenOutputToFile_thenIsAsExpected() = runTest {
        // TODO: Issue #50
        //  Remove hasRedirect
        if (!canRunTor || !hasRedirect) {
            println("Skipping...")
            return@runTest
        }

        val logsDir = homeDir.resolve("logs")
        val stdoutFile = logsDir.resolve("tor.log")
        val stderrFile = logsDir.resolve("tor.err")

        stdoutFile.delete()
        stderrFile.delete()
        logsDir.delete()

        assertFalse(logsDir.exists())

        installer.install().toProcessBuilder()
            .stdout(Stdio.File.of(stdoutFile))
            .stderr(Stdio.File.of(stderrFile))
            .spawn { p ->
                println(p)

                // parent dir was created by Stdio.Config.Builder.build
                assertTrue(stdoutFile.exists())
                assertTrue(stderrFile.exists())

                p.waitForAsync(2.seconds, ::delay)
                p
            }.waitForAsync(::delay)


        assertTrue(stdoutFile.readUtf8().lines().first().contains(" [notice] Tor "))

        if (!isAndroidRuntime) {
            // Android might post up a linker error
            // WARNING: linker: /data/app/io.matthewnelson.kmp.process.testing.test-2/lib/x86/libtor.so: unsupported flags DT_FLAGS_1=0x8000001
            assertTrue(stderrFile.readUtf8().isEmpty())
        }
    }

    @Test
    fun givenExecutable_whenOutput_thenIsAsExpected() {
        if (!canRunTor) {
            println("Skipping...")
            return
        }

        val out = installer.install().toProcessBuilder()
            .output { timeoutMillis = 2_000 }

        println(out)

        assertEquals(expectedTorExitCode, out.processInfo.exitCode)
        assertTrue(out.stdout.lines().first().contains(" [notice] Tor "))

        if (!isAndroidRuntime) {
            // Android might post up a linker error
            // WARNING: linker: /data/app/io.matthewnelson.kmp.process.testing.test-2/lib/x86/libtor.so: unsupported flags DT_FLAGS_1=0x8000001
            assertTrue(out.stderr.isEmpty())
        }
    }

    @Test
    fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected() = runTest {
        if (!canRunTor) {
            println("Skipping...")
            return@runTest
        }

        installer.install().toProcessBuilder().spawn { p2 ->
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

            p2.waitForAsync(2.seconds, ::delay)

            p2.destroy()

            withContext(Dispatchers.Default) { delay(250.milliseconds) }

            val stdoutString = stdoutBuilder.toString()
            val stderrString = stderrBuilder.toString()
            println(stdoutString)
            println(stderrString)

            assertEquals(expectedTorExitCode, p2.exitCode())
            assertTrue(stdoutString.lines().first().contains(" [notice] Tor "))

            if (!isAndroidRuntime) {
                // Android might post up a linker error
                // WARNING: linker: /data/app/io.matthewnelson.kmp.process.testing.test-2/lib/x86/libtor.so: unsupported flags DT_FLAGS_1=0x8000001
                assertTrue(stderrString.isEmpty())
            }
        }
    }

    private fun Process.Builder.envHome(): Process.Builder = environment("HOME", homeDir.path)

    private fun ResourceInstaller.Paths.Tor.toProcessBuilder(): Process.Builder {
        return Process.Builder(executable = tor)
            .args("--DataDirectory")
            .args(dataDir.path)
            .args("--CacheDirectory")
            .args(cacheDir.path)
            .args("--GeoIPFile")
            .args(geoip.path)
            .args("--GeoIPv6File")
            .args(geoip6.path)
            .args("--CookieAuthFile")
            .args(homeDir.resolve("control_auth_cookie").path)
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
            .envHome()
            .stdin(Stdio.Null)
            .stdout(Stdio.Pipe)
            .stderr(Stdio.Pipe)
    }
}
