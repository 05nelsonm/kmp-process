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

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessAndroidTest: ProcessBaseTest() {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext

    override val homeDir: File get() = ctx.getDir("torservice", Context.MODE_PRIVATE)
    override val cacheDir: File get() = SysTempDir.resolve("torservice").resolve("cache")

    override fun assertExitCode(code: Int) {
        val expected = if (android.os.Build.VERSION.SDK_INT < 24) Signal.SIGKILL.code else 0
        assertEquals(expected, code)
    }

    @Test
    override fun givenCurrentProcess_whenPid_thenSucceeds() {
        super.givenCurrentProcess_whenPid_thenSucceeds()

        val expected = android.os.Process.myPid()
        assertEquals(expected, Process.Current.pid())
    }

    @Test
    override fun givenCurrentProcess_whenEnvironment_thenIsNotEmpty() {
        super.givenCurrentProcess_whenEnvironment_thenIsNotEmpty()
    }

    @Test
    override fun givenExecutable_whenOutputToFile_thenIsAsExpected(): TestResult {
        // TODO: Issue #50
        //  Reduce to API 21
        if (android.os.Build.VERSION.SDK_INT < 24) return

        return super.givenExecutable_whenOutputToFile_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenOutput_thenIsAsExpected() {
        if (android.os.Build.VERSION.SDK_INT < 21) return

        super.givenExecutable_whenOutput_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected(): TestResult {
        if (android.os.Build.VERSION.SDK_INT < 21) return

        return super.givenExecutable_whenPipeOutputFeeds_thenIsAsExpected()
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsStatusCode() {
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
        val output = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 1; exit 42")
            .destroySignal(Signal.SIGKILL)
            // Should be killed before completing via signal
            .output{ timeoutMillis = 250 }

        val actual = output
            .processInfo
            .exitCode

        val expected = output
            .processInfo
            .destroySignal
            .code

        assertEquals(expected, actual)
    }
}
