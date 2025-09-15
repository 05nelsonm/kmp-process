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
import io.matthewnelson.kmp.process.*
import io.matthewnelson.kmp.process.internal.RealLineOutputFeed.Companion.LF
import io.matthewnelson.kmp.process.internal.js.JsInt8Array
import io.matthewnelson.kmp.process.internal.js.fill
import io.matthewnelson.kmp.process.internal.js.toJsArray
import io.matthewnelson.kmp.process.internal.node.ModuleFs
import io.matthewnelson.kmp.process.internal.node.node_fs
import io.matthewnelson.kmp.process.internal.node.node_stream

// jsMain
internal actual class PlatformBuilder private actual constructor() {

    internal var detached: Boolean = false
    // String or Boolean
    internal var shell: Any = false
    internal var windowsVerbatimArguments = false
    internal var windowsHide: Boolean = true

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
        node_stream
        val jsEnv = env.toJsEnv()
        val jsStdio = try {
            stdio.toJsStdio()
        } catch (e: IOException) {
            options.dropInput()
            throw e
        }

        val input = jsStdio.closeDescriptorsOnFailure {
            val b = options.consumeInput() ?: return@closeDescriptorsOnFailure null
            val a = b.toJsArray(factory = ::JsInt8Array)
            b.fill(0)
            a
        }

        val opts = js("{}")
        chdir?.let { opts["cwd"] = it.path }
        input?.let { opts["input"] = it }
        opts["stdio"] = jsStdio
        opts["env"] = jsEnv
        opts["timeout"] = options.timeout.inWholeMilliseconds.toInt()
        opts["killSignal"] = destroy.name
        opts["maxBuffer"] = options.maxBuffer
        opts["shell"] = shell
        opts["windowsVerbatimArguments"] = windowsVerbatimArguments
        opts["windowsHide"] = windowsHide

        val output = jsStdio.closeDescriptorsOnFailure {
            try {
                child_process_spawnSync(command, args.toTypedArray(), opts)
            } finally {
                input?.fill()
            }
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
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process {
        node_stream
        val jsEnv = env.toJsEnv()
        val jsStdio = stdio.toJsStdio()
        val isDetached = detached

        val opts = js("{}")
        chdir?.let { opts["cwd"] = it.path }
        opts["env"] = jsEnv
        opts["stdio"] = jsStdio
        opts["detached"] = isDetached
        opts["shell"] = shell
        opts["windowsVerbatimArguments"] = windowsVerbatimArguments
        opts["windowsHide"] = windowsHide
        opts["killSignal"] = destroy.name

        val jsProcess = jsStdio.closeDescriptorsOnFailure {
            child_process_spawn(command, args.toTypedArray(), opts)
        }

        return NodeJsProcess(
            jsProcess,
            isDetached,
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
            handler,
        )
    }

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        private fun Map<String, String>.toJsEnv(): dynamic {
            val jsEnv = js("{}")
            entries.forEach { entry ->
                jsEnv[entry.key] = entry.value
            }
            return jsEnv
        }

        // @Throws(IOException::class, UnsupportedOperationException::class)
        private fun Stdio.Config.toJsStdio(): Array<Any> {
            val fs = node_fs
            val jsStdio = Array<Any>(3) { "pipe" }

            jsStdio.closeDescriptorsOnFailure {
                jsStdio[0] = stdin.toJsStdio(fs, isStdin = true)
                jsStdio[1] = stdout.toJsStdio(fs, isStdin = false)
                jsStdio[2] = if (isStderrSameFileAsStdout()) {
                    // use the same file descriptor
                    jsStdio[1]
                } else {
                    stderr.toJsStdio(fs, isStdin = false)
                }
            }

            return jsStdio
        }

        // @Throw(Throwable::class)
        private fun Stdio.toJsStdio(
            fs: ModuleFs,
            isStdin: Boolean,
        ): Any = when (this) {
            is Stdio.Inherit -> "inherit"
            is Stdio.Pipe -> "pipe"
            is Stdio.File -> when {
                file == STDIO_NULL -> "ignore"
                isStdin -> {
                    val fd = jsExternTryCatch { fs.openSync(file.path, "r") }

                    try {
                        val isDirectory = jsExternTryCatch { fs.fstatSync(fd).isDirectory() }
                        if (isDirectory) throw FileSystemException(file, null, "EISDIR: Is a Directory")
                    } catch (t: Throwable) {
                        try {
                            jsExternTryCatch { fs.closeSync(fd) }
                        } catch (tt: Throwable) {
                            t.addSuppressed(tt)
                        }
                        throw t
                    }

                    fd
                }
                append -> jsExternTryCatch { fs.openSync(file.path, "a") }
                else -> jsExternTryCatch { fs.openSync(file.path, "w") }
            }
        }

        // @Throws(IOException::class)
        private inline fun <T: Any?> Array<Any>.closeDescriptorsOnFailure(
            block: () -> T,
        ): T {
            val result = try {
                block()
            } catch (t: Throwable) {
                forEach { stdio ->
                    val fd = stdio as? Double ?: return@forEach

                    try {
                        node_fs.closeSync(fd)
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    }
                }

                throw t.toIOException()
            }

            return result
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Buffer.toUtf8Trimmed(): String {
            var limit = length.toInt()
            if (limit == 0) return ""

            if (readInt8(limit - 1) == LF) limit--
            return toUtf8(end = limit)
        }
    }
}
