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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NewApi")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

// jvmMain
internal actual class PlatformBuilder private actual constructor() {

    private val jProcessBuilder = ProcessBuilder(emptyList())
    internal actual val env: MutableMap<String, String> by lazy {
        jProcessBuilder.environment()
    }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process {

        val jCommands = ArrayList<String>(args.size + 1)
        jCommands.add(command)
        jCommands.addAll(args)

        jProcessBuilder.command(jCommands)

        // NOTE: do not modify jProcessBuilder environment.
        //  The env value passed here is what is currently set
        //  for jProcessBuilder (which is Mutable). The immutable
        //  env value passed here is simply what gets used for
        //  Process.environment.

        if (ANDROID_SDK_INT?.let { sdkInt -> sdkInt >= 24 } != false) {
            // Only available on Android Runtime 24+ & Java 8+
            jProcessBuilder.redirectInput(stdio.stdin.toRedirect(isStdin = true))
            jProcessBuilder.redirectOutput(stdio.stdout.toRedirect(isStdin = false))
            jProcessBuilder.redirectError(stdio.stderr.toRedirect(isStdin = false))
        } else {
            // TODO: Issue #50
            //  Supplement Stdio behavior for Android API < 24
        }

        val destroySignal = ANDROID_SDK_INT?.let { sdkInt ->
            when {
                // API < 24 always utilizes SIGKILL
                // when destroy is called. This reflects that.
                sdkInt < 24 -> Signal.SIGKILL
                // API 24+ always uses SIGTERM as
                // destroyForcibly does nothing but call
                // destroy under the hood.
                else -> Signal.SIGTERM
            }
        } ?: destroy

        val jProcess = jProcessBuilder.start()

        return JvmProcess.of(
            jProcess,
            command,
            args,
            env,
            stdio,
            destroySignal,
        )
    }

    internal actual companion object {

        @JvmSynthetic
        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        @JvmSynthetic
        internal actual fun myPid(): Int {
            AndroidMyPidMethod?.let { myPid ->
                return myPid.invoke(null) as Int
            }

            return java.lang.management.ManagementFactory
                .getRuntimeMXBean()
                .name
                .split('@')[0]
                .toInt()
        }

        private val AndroidMyPidMethod by lazy {
            // Not Android Runtime
            if (ANDROID_SDK_INT == null) return@lazy null

            // Android runtime
            Class.forName("android.os.Process")
                .getMethod("myPid")
        }

        private fun Stdio.toRedirect(
            isStdin: Boolean,
        ): ProcessBuilder.Redirect = when (this) {
            is Stdio.Inherit -> ProcessBuilder.Redirect.INHERIT
            is Stdio.Pipe -> ProcessBuilder.Redirect.PIPE
            is Stdio.File -> when {
                file == STDIO_NULL -> if (isStdin) {
                    REDIRECT_NULL_READ
                } else {
                    REDIRECT_NULL_WRITE
                }
                isStdin -> ProcessBuilder.Redirect.from(file)
                append -> ProcessBuilder.Redirect.appendTo(file)
                else -> ProcessBuilder.Redirect.to(file)
            }
        }

        private val REDIRECT_NULL_READ: ProcessBuilder.Redirect by lazy {
            ProcessBuilder.Redirect.from(REDIRECT_NULL_WRITE.file())
        }

        private val REDIRECT_NULL_WRITE: ProcessBuilder.Redirect by lazy {
            val discard = try {
                Class.forName("java.lang.ProcessBuilder\$Redirect")
                    ?.getField("DISCARD")
                    ?.get(null) as? ProcessBuilder.Redirect
            } catch (_: Throwable) {
                null
            }

            if (discard != null) return@lazy discard

            ProcessBuilder.Redirect.to(STDIO_NULL)
        }
    }
}
