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
@file:OptIn(DelicateFileApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "LocalVariableName")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.*
import io.matthewnelson.kmp.process.internal.RealLineOutputFeed.Companion.LF
import io.matthewnelson.kmp.process.internal.js.JsArray
import io.matthewnelson.kmp.process.internal.js.JsError
import io.matthewnelson.kmp.process.internal.js.JsInt8Array
import io.matthewnelson.kmp.process.internal.js.JsObject
import io.matthewnelson.kmp.process.internal.js.fill
import io.matthewnelson.kmp.process.internal.js.getString
import io.matthewnelson.kmp.process.internal.js.getJsAny
import io.matthewnelson.kmp.process.internal.js.getInt
import io.matthewnelson.kmp.process.internal.js.getIntOrNull
import io.matthewnelson.kmp.process.internal.js.getJsAnyOrNull
import io.matthewnelson.kmp.process.internal.js.getStringOrNull
import io.matthewnelson.kmp.process.internal.js.new
import io.matthewnelson.kmp.process.internal.js.set
import io.matthewnelson.kmp.process.internal.js.toJsArray
import io.matthewnelson.kmp.process.internal.node.JsBuffer
import io.matthewnelson.kmp.process.internal.node.ModuleFs
import io.matthewnelson.kmp.process.internal.node.asBuffer
import io.matthewnelson.kmp.process.internal.node.node_child_process
import io.matthewnelson.kmp.process.internal.node.node_fs
import io.matthewnelson.kmp.process.internal.node.node_process
import io.matthewnelson.kmp.process.internal.node.node_stream
import kotlin.let

// jsWasmJsMain
internal actual class PlatformBuilder private actual constructor() {

    internal var detached: Boolean = false
    // String or Boolean
    internal var shell: Any = false
    internal var windowsVerbatimArguments = false
    internal var windowsHide: Boolean = true

    internal actual val env: MutableMap<String, String> by lazy {
        try {
            val envParent = node_process.env
            val keys = JsObject.keys(envParent)

            val map = LinkedHashMap<String, String>(keys.length, 1.0F)

            for (i in 0 until keys.length) {
                val key = keys.getString(i)
                map[key] = envParent.getString(key)
            }

            map
        } catch (_: Throwable) {
            // Js/WasmJs Browser
            LinkedHashMap(1, 1.0F)
        }
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
    ): Output {
        node_stream
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

        val opts = JsObject.new()
        chdir?.let { opts["cwd"] = it.path }
        input?.let { opts["input"] = it }
        opts["stdio"] = jsStdio.toJsArray()
        opts["env"] = env.toJsObject()
        opts["timeout"] = options.timeout.inWholeMilliseconds.toInt()
        opts["killSignal"] = destroy.name
        opts["maxBuffer"] = options.maxBuffer
        when (val _shell = shell) {
            is String -> opts["shell"] = _shell
            is Boolean -> opts["shell"] = _shell
        }
        opts["windowsVerbatimArguments"] = windowsVerbatimArguments
        opts["windowsHide"] = windowsHide

        val output = jsStdio.closeDescriptorsOnFailure {
            try {
                node_child_process.let { m ->
                    jsExternTryCatch { m.spawnSync(command, args.toJsArray(), opts) }
                }
            } finally {
                input?.fill()
            }
        }

        val pid = output.getInt("pid")

        val stdout = output.getJsAny<JsBuffer>("stdout").asBuffer().let { buf ->
            val utf8 = buf.toUtf8Trimmed()
            buf.fill()
            utf8
        }

        val stderr = output.getJsAny<JsBuffer>("stderr").asBuffer().let { buf ->
            val utf8 = buf.toUtf8Trimmed()
            buf.fill()
            utf8
        }

        val code: Int = output.getIntOrNull("status").let { status ->
            if (status != null) return@let status

            val signal = output.getStringOrNull("signal")
            try {
                Signal.valueOf(signal!!)
            } catch (_: Throwable) {
                destroy
            }.code
        }

        val processError: String? = try {
            output.getJsAnyOrNull<JsError>("error")?.message
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
        node_stream
        val jsStdio = stdio.toJsStdio()
        val isDetached = detached

        val opts = JsObject.new()
        chdir?.let { opts["cwd"] = it.path }
        opts["env"] = env.toJsObject()
        opts["stdio"] = jsStdio.toJsArray()
        opts["detached"] = isDetached
        when (val _shell = shell) {
            is String -> opts["shell"] = _shell
            is Boolean -> opts["shell"] = _shell
        }
        opts["windowsVerbatimArguments"] = windowsVerbatimArguments
        opts["windowsHide"] = windowsHide
        opts["killSignal"] = destroy.name

        val jsProcess = jsStdio.closeDescriptorsOnFailure {
            node_child_process.let { m ->
                jsExternTryCatch { m.spawn(command, args.toJsArray(), opts) }
            }
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

        private fun Map<String, String>.toJsObject(): JsObject {
            val obj = JsObject.new()
            entries.forEach { (key, value) -> obj[key] = value }
            return obj
        }

        // Accepts only String and Double
        @Throws(IllegalStateException::class)
        private fun List<Any>.toJsArray(): JsArray {
            val array = JsArray.of(size)
            for (i in indices) {
                when (val value = this[i]) {
                    is String -> array[i] = value
                    is Double -> array[i] = value
                    else -> throw IllegalStateException("Unknown type[${value::class}]")
                }
            }
            return array
        }

        @Throws(IOException::class, UnsupportedOperationException::class)
        private fun Stdio.Config.toJsStdio(): List<Any> {
            val fs = node_fs
            val jsStdio = ArrayList<Any>(3)
            jsStdio.closeDescriptorsOnFailure {
                stdin.toJsStdio(fs, isStdin = true).let { jsStdio.add(it) }
                stdout.toJsStdio(fs, isStdin = false).let { jsStdio.add(it) }
                if (isStderrSameFileAsStdout()) {
                    // use the same file descriptor as stdout
                    jsStdio[1]
                } else {
                    stderr.toJsStdio(fs, isStdin = false)
                }.let { jsStdio.add(it) }
            }
            return jsStdio
        }

        @Throws(Throwable::class)
        private fun Stdio.toJsStdio(fs: ModuleFs, isStdin: Boolean): Any = when (this) {
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

        @Throws(IOException::class)
        private inline fun <T: Any?> List<Any>.closeDescriptorsOnFailure(block: () -> T): T = try {
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

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Buffer.toUtf8Trimmed(): String {
            var limit = length.toInt()
            if (limit == 0) return ""

            if (readInt8(limit - 1) == LF) limit--
            return toUtf8(end = limit)
        }
    }
}
