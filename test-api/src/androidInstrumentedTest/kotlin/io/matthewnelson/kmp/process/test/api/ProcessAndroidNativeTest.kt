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
package io.matthewnelson.kmp.process.test.api

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ProcessAndroidNativeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext
    private val nativeLibraryDir = ctx.applicationInfo.nativeLibraryDir.toFile().absoluteFile

    @Test
    fun givenAndroidNative_whenExecuteApiTestBinary_thenIsSuccessful() {
        run("libTestApiExec.so", 3.minutes)
    }

    @Test
    fun givenAndroidNative_whenExecuteProcessTestBinary_thenIsSuccessful() {
        run("libTestProcessExec.so", 2.minutes)
    }

    private fun run(libName: String, timeout: Duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            println("Skipping...")
            return
        }

        val process = Process.Builder(nativeLibraryDir.resolve(libName))
            .stdin(Stdio.Null)
            .spawn { p ->
                // Cannot use Stdio.Inherit, otherwise will not be captured by logcat.
                p.stdoutFeed { line ->
                    println(line ?: "STDOUT: END")
                }.stderrFeed { line ->
                    System.err.println(line ?: "STDERR: END")
                }.waitFor(timeout)
                p
            }

        if (process.waitFor() == 0) return

        System.err.println(process.toString())
        System.err.println("--- ENVIRONMENT ---")
        process.environment.forEach { (key, value) -> System.err.println("$key=$value") }
        throw AssertionError("Process.exitCode[${process.exitCode()}] != 0")
    }
}
