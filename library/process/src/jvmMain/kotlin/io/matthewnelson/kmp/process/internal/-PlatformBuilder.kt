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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.*
import java.lang.reflect.Method

// jvmMain
internal actual class PlatformBuilder private actual constructor() {

    private val jProcessBuilder = ProcessBuilder(emptyList())
    internal actual val env: MutableMap<String, String> by lazy {
        val e = jProcessBuilder.environment()

        // Android API 24 to 32 caches the C environment in ProcessEnvironment and
        // does not update with any modifications that may have taken place via native
        // code or via android.system.Os (API 21+). That is insane...
        val eOS = ANDROID_OS_ENVIRON?.invoke(null) ?: return@lazy e

        // Clear out the stale environment and replace with the proper one.
        e.clear()
        @Suppress("UNCHECKED_CAST")
        (eOS as Array<out String>).forEach { line ->
            val i = line.indexOf('=')
            if (i == -1) return@forEach
            e[line.substring(0, i)] = line.substring(i + 1, line.length)
        }

        e
    }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, chdir, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process {

        val android23Stdio = AndroidApi23Stdio.getOrNull(stdio)
        val isStderrSameFileAsStdout = android23Stdio?.isStderrSameFileAsStdout ?: stdio.isStderrSameFileAsStdout()
        jProcessBuilder.redirectErrorStream(/* redirectErrorStream = */ isStderrSameFileAsStdout)

        @Suppress("NewApi")
        if (android23Stdio == null) {
            // Android API 24+ or Java 8+
            jProcessBuilder.redirectInput(stdio.stdin.toRedirect(isStdin = true))
            jProcessBuilder.redirectOutput(stdio.stdout.toRedirect(isStdin = false))

            if (isStderrSameFileAsStdout) {
                // Always set to default in case this builder is being reused
                ProcessBuilder.Redirect.PIPE
            } else {
                stdio.stderr.toRedirect(isStdin = false)
            }.let { redirect -> jProcessBuilder.redirectError(redirect) }
        }

        val jCommands = ArrayList<String>(args.size + 1)
        jCommands.add(command)
        jCommands.addAll(args)

        jProcessBuilder.command(jCommands)
        jProcessBuilder.directory(chdir)

        // NOTE: do not modify jProcessBuilder environment.
        //  The env value passed to this function is what is currently
        //  set for jProcessBuilder (which is Mutable). When start is called
        //  ProcessBuilder uses its environment reference. The immutable env
        //  value here is simply what gets exposed to API consumers via
        //  Process.environment.

        val destroySignal = ANDROID.SDK_INT?.let { sdkInt ->
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

        val jProcess = try {
            jProcessBuilder.start()
        } catch (t: Throwable) {
            try {
                android23Stdio?.close()
            } catch (tt: Throwable) {
                t.addSuppressed(tt)
            }
            throw t.wrapIOException()
        }

        return JvmProcess.of(
            jProcess,
            android23Stdio,
            command,
            args,
            chdir,
            env,
            android23Stdio?.stdio ?: stdio,
            destroySignal,
            handler,
        )
    }

    internal actual companion object {

        @JvmSynthetic
        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        @Suppress("NewApi")
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

        private val ANDROID_OS_ENVIRON: Method? by lazy {
            if (ANDROID.SDK_INT?.let { it in 24..32 } != true) return@lazy null
            @Suppress("UNNECESSARY_SAFE_CALL")
            Class.forName("android.system.Os")?.getMethod("environ")
        }

        @Suppress("NewApi")
        private val REDIRECT_NULL_READ: ProcessBuilder.Redirect by lazy {
            ProcessBuilder.Redirect.from(REDIRECT_NULL_WRITE.file())
        }

        @Suppress("NewApi")
        private val REDIRECT_NULL_WRITE: ProcessBuilder.Redirect by lazy {
            val discard = try {
                @Suppress("UNNECESSARY_SAFE_CALL")
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
