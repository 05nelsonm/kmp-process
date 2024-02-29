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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "LocalVariableName", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

// jsMain
internal actual class PlatformBuilder private actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        try {
            val env = js("require('process')").env
            val keys = js("Object").keys(env).unsafeCast<Array<String>>()

            val map = LinkedHashMap<String, String>(keys.size, 1.0F)

            keys.forEach { key ->
                map[key] = env[key] as String
            }

            map
        } catch (_: Throwable) {
            LinkedHashMap(1, 1.0F)
        }
    }

    // @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output {
        val jsEnv = env.toJsEnv()
        val (jsStdio, descriptors) = stdio.toJsStdio()

        val opts = js("{}")
        chdir?.let { opts["cwd"] = it.path }
        opts["stdio"] = jsStdio
        opts["env"] = jsEnv
        opts["timeout"] = options.timeout.inWholeMilliseconds.toInt()
        opts["killSignal"] = destroy.name
        opts["maxBuffer"] = options.maxBuffer
        opts["shell"] = false
        opts["windowsVerbatimArguments"] = false
        opts["windowsHide"] = true

        val output = descriptors.closeOnFailure {
            child_process_spawnSync(command, args.toTypedArray(), opts)
        }

        val pid = output["pid"] as Int

        val stdout = Buffer.wrap(output["stdout"]).let { buf ->
            val utf8 = buf.toUtf8Trimmed()
            buf.fill()
            utf8
        }

        val stderr = Buffer.wrap(output["stderr"]).let { buf ->
            val utf8 = buf.toUtf8Trimmed()
            buf.fill()
            utf8
        }

        val code: Int = (output["status"] as? Number)?.toInt().let { status ->
            if (status != null) return@let status

            val signal = output["signal"] as? String
            try {
                Signal.valueOf(signal!!)
            } catch (_: Throwable) {
                destroy
            }.code
        }

        val processError: String? = try {
            output["error"].message as? String
        } catch (_: Throwable) {
            null
        }

        return Output.ProcessInfo.createOutput(
            stdout,
            stderr,
            processError,
            pid,
            code,
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
        )
    }

    // @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal
    ): Process {
        val jsEnv = env.toJsEnv()
        val (jsStdio, descriptors) = stdio.toJsStdio()

        val opts = js("{}")
        chdir?.let { opts["cwd"] = it.path }
        opts["env"] = jsEnv
        opts["stdio"] = jsStdio
        opts["detached"] = false
        opts["shell"] = false
        opts["windowsVerbatimArguments"] = false
        opts["windowsHide"] = true
        opts["killSignal"] = destroy.name

        val jsProcess = descriptors.closeOnFailure {
            child_process_spawn(command, args.toTypedArray(), opts)
        }

        return NodeJsProcess(
            jsProcess,
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
        )
    }

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        internal actual fun myPid(): Int = process_pid

        private fun Map<String, String>.toJsEnv(): dynamic {
            val jsEnv = js("{}")
            entries.forEach { entry ->
                jsEnv[entry.key] = entry.value
            }
            return jsEnv
        }

        // @Throws(IOException::class)
        private fun Stdio.Config.toJsStdio(): Pair<Array<Any>, Array<Number?>> {
            val descriptors = Array<Number?>(3) { null }

            return descriptors.closeOnFailure {
                val jsStdin = stdin.toJsStdio(isStdin = true)
                descriptors[0] = jsStdin as? Number

                val jsStdout = stdout.toJsStdio(isStdin = false)
                descriptors[1] = jsStdout as? Number

                val jsStderr = if (isStderrSameFileAsStdout) {
                    // use the same file descriptor
                    jsStdout
                } else {
                    val stdio = stderr.toJsStdio(isStdin = false)
                    descriptors[2] = stdio as? Number
                    stdio
                }

                arrayOf(jsStdin, jsStdout, jsStderr)
            } to descriptors
        }

        // @Throw(Throwable::class)
        private fun Stdio.toJsStdio(
            isStdin: Boolean,
        ): Any = when (this) {
            is Stdio.Inherit -> "inherit"
            is Stdio.Pipe -> "pipe"
            is Stdio.File -> when {
                file == STDIO_NULL -> "ignore"
                isStdin -> fs_openSync(file.path, "r")
                append -> fs_openSync(file.path, "a")
                else -> fs_openSync(file.path, "w")
            }
        }

        // @Throws(IOException::class)
        private inline fun <T: Any> Array<Number?>.closeOnFailure(
            block: () -> T,
        ): T {
            val result = try {
                block()
            } catch (t: Throwable) {
                forEach { fd ->
                    if (fd == null) return@forEach
                    try {
                        fs_closeSync(fd)
                    } catch (_: Throwable) {}
                }

                throw t.toIOException()
            }

            return result
        }

        private const val N = '\n'.code.toByte()

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Buffer.toUtf8Trimmed(): String {
            var limit = length.toInt()
            if (limit == 0) return ""

            if (readInt8(limit - 1) == N) limit--
            return toUtf8(end = limit)
        }
    }
}
