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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

// jvmMain
internal actual class PlatformBuilder internal actual constructor() {

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
    ): Output {

        val (jProcess, process) = spawnJvm(command, args, env, stdio, destroy)

        val result: Output = try {
            StdioOutputEater.of(
                maxBuffer = options.maxBuffer,
                stdout = jProcess.inputStream,
                stderr = jProcess.errorStream,
            ).use { eater ->

                var waitForCode: Int? = null
                try {
                    waitForCode = process.commonWaitFor(options.timeout) {
                        if (eater.maxBufferExceeded) {
                            throw IllegalStateException()
                        }

                        Thread.sleep(it.inWholeMilliseconds)
                    }
                } catch (e: InterruptedException) {
                    throw IOException("Underlying thread interrupted", e)
                } catch (e: IllegalStateException) {
                    // maxBuffer was exceeded and it hopped out of waitFor
                } finally {
                    // whether timed out or not, always destroy
                    // so that streams are closed
                    process.destroy()
                }

                val pErr = StringBuilder()
                if (waitForCode == null) {
                    pErr.append("waitFor timed out")
                }

                val exitCode = waitForCode ?: try {
                    // await for final closure if not ready yet
                    process.waitFor()
                } catch (e: InterruptedException) {
                    throw IOException("Underlying thread interrupted", e)
                }

                val (stdout, stderr) = eater.doFinal()

                if (eater.maxBufferExceeded) {
                    if (pErr.isNotEmpty()) {
                        pErr.append(". ")
                    }
                    pErr.append("maxBuffer[${options.maxBuffer}] exceeded")
                }

                Output.ProcessInfo.createOutput(
                    stdout,
                    stderr,
                    if (pErr.isEmpty()) null else pErr.toString(),
                    process.pid(),
                    exitCode,
                    process.command,
                    process.args,
                    process.environment,
                    process.stdio,
                    process.destroySignal
                )
            }
        } finally {
            process.destroy()
        }

        return result
    }

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process = spawnJvm(command, args, env, stdio, destroy).second

    @Throws(IOException::class)
    private fun spawnJvm(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Pair<java.lang.Process, JvmProcess> {

        val jCommands = ArrayList<String>(args.size + 1)
        jCommands.add(command)
        jCommands.addAll(args)

        jProcessBuilder.command(jCommands)

        jProcessBuilder.redirectInput(stdio.stdin.toRedirect(isStdin = true))
        jProcessBuilder.redirectOutput(stdio.stdout.toRedirect(isStdin = false))
        jProcessBuilder.redirectError(stdio.stderr.toRedirect(isStdin = false))

        val jProcess = jProcessBuilder.start()

        val destroySignal = when {
            destroy == Signal.SIGTERM -> destroy
            else -> ANDROID_SDK_INT?.let { sdkInt ->
                // destroyForcibly only available on
                // Android Runtime for API 26+
                if (sdkInt < 26) Signal.SIGTERM else null
            } ?: destroy
        }

        return jProcess to JvmProcess.of(
            jProcess,
            command,
            args,
            env,
            stdio,
            destroySignal,
        )
    }

    private companion object {

        private fun Stdio.toRedirect(
            isStdin: Boolean,
        ): ProcessBuilder.Redirect = when (this) {
            is Stdio.Inherit -> ProcessBuilder.Redirect.INHERIT
            is Stdio.Pipe -> ProcessBuilder.Redirect.PIPE
            is Stdio.File -> {
                when {
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
