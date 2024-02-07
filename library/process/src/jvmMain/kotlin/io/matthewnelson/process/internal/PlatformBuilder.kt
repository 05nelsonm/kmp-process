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

package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.ProcessException
import io.matthewnelson.process.Stdio
import java.io.File
import java.io.IOException

// jvmMain
internal actual class PlatformBuilder internal actual constructor() {

    private val jProcessBuilder = ProcessBuilder(emptyList())
    internal actual val env: MutableMap<String, String> by lazy {
        jProcessBuilder.environment()
    }

    @Throws(ProcessException::class)
    internal actual fun build(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
    ): Process {
        val jCommands = ArrayList<String>(args.size + 1)
        jCommands.add(command)
        jCommands.addAll(args)

        jProcessBuilder.command(jCommands)
        jProcessBuilder.configureRedirects(stdio)

        val jProcess = try {
            jProcessBuilder.start()
        } catch (e: IOException) {
            throw ProcessException(e)
        }

        return JvmProcess.of(command, args, env, stdio, jProcess)
    }

    private companion object {

        @Throws(ProcessException::class)
        private fun ProcessBuilder.configureRedirects(stdio: Stdio.Config) {
            redirectInput(stdio.stdin.toRedirect(isStdin = true))
            redirectOutput(stdio.stdout.toRedirect(isStdin = false))
            redirectError(stdio.stderr.toRedirect(isStdin = false))

            listOf<File?>(
                redirectInput().file(),
                redirectOutput().file(),
                redirectError().file(),
            ).forEach { file ->
                if (file == null) return@forEach
                if (file.path == PATH_STDIO_NULL) return@forEach
                val parent = file.parentFile ?: return@forEach
                if (!parent.exists() && !parent.mkdirs()) {
                    throw ProcessException("Failed to mkdirs for $parent")
                }
            }
        }

        private fun Stdio.toRedirect(
            isStdin: Boolean,
        ): ProcessBuilder.Redirect = when (this) {
            is Stdio.Inherit -> ProcessBuilder.Redirect.INHERIT
            is Stdio.Pipe -> ProcessBuilder.Redirect.PIPE
            is Stdio.File -> {
                when {
                    path == PATH_STDIO_NULL -> if (isStdin) {
                        REDIRECT_NULL_READ
                    } else {
                        REDIRECT_NULL_WRITE
                    }
                    isStdin -> ProcessBuilder.Redirect.from(File(path))
                    append -> ProcessBuilder.Redirect.appendTo(File(path))
                    else -> ProcessBuilder.Redirect.to(File(path))
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

            ProcessBuilder.Redirect.to(File(PATH_STDIO_NULL))
        }
    }
}
