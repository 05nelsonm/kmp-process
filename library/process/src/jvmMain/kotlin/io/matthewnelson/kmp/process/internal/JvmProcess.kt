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

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import kotlin.time.Duration

internal class JvmProcess private constructor(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    private val jProcess: java.lang.Process,
): Process(command, args, env, stdio) {

    @Throws(IllegalStateException::class)
    override fun exitCode(): Int {
        val result: Int? = try {
            jProcess.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }

        return result ?: throw IllegalStateException("Process hasn't exited")
    }

    override fun sigterm(): Process {
        jProcess.destroy()
        return this
    }

    override fun sigkill(): Process {
        ANDROID_SDK_INT?.let { sdkInt ->
            // Android runtime, check API version
            if (sdkInt >= 26) {
                jProcess.destroyForcibly()
            } else {
                jProcess.destroy()
            }
        } ?: jProcess.destroyForcibly()
        return this
    }

    @Throws(InterruptedException::class)
    override fun waitFor(): Int = jProcess.waitFor()
    @Throws(InterruptedException::class)
    override fun waitFor(timeout: Duration): Int? = commonWaitFor(timeout) { Thread.sleep(it.inWholeMilliseconds) }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            command: String,
            args: List<String>,
            env: Map<String, String>,
            stdio: Stdio.Config,
            jProcess: java.lang.Process
        ): JvmProcess = JvmProcess(
            command,
            args,
            env,
            stdio,
            jProcess,
        )

        private val ANDROID_SDK_INT: Int? by lazy {

            if (
                System.getProperty("java.runtime.name")
                    ?.contains("android", ignoreCase = true) != true
            ) {
                // Not Android runtime
                return@lazy null
            }

            try {
                val clazz = Class.forName("android.os.Build\$VERSION")

                try {
                    clazz?.getField("SDK_INT")?.getInt(null)
                } catch (_: Throwable) {
                    clazz?.getField("SDK")?.get(null)?.toString()?.toIntOrNull()
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
