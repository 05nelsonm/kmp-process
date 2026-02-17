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

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileSystemException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.async.AsyncFs
import io.matthewnelson.kmp.file.get
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.RealLineOutputFeed.Companion.LF
import io.matthewnelson.kmp.process.internal.js.JsArray
import io.matthewnelson.kmp.process.internal.js.JsInt8Array
import io.matthewnelson.kmp.process.internal.js.JsObject
import io.matthewnelson.kmp.process.internal.js.fill
import io.matthewnelson.kmp.process.internal.js.getString
import io.matthewnelson.kmp.process.internal.js.getJsBufferOrNull
import io.matthewnelson.kmp.process.internal.js.getInt
import io.matthewnelson.kmp.process.internal.js.getIntOrNull
import io.matthewnelson.kmp.process.internal.js.getJsErrorOrNull
import io.matthewnelson.kmp.process.internal.js.getStringOrNull
import io.matthewnelson.kmp.process.internal.js.new
import io.matthewnelson.kmp.process.internal.js.set
import io.matthewnelson.kmp.process.internal.js.toJsArray
import io.matthewnelson.kmp.process.internal.js.toThrowable
import io.matthewnelson.kmp.process.internal.node.JsBuffer
import io.matthewnelson.kmp.process.internal.node.JsStats
import io.matthewnelson.kmp.process.internal.node.ModuleFs
import io.matthewnelson.kmp.process.internal.node.asBuffer
import io.matthewnelson.kmp.process.internal.node.node_child_process
import io.matthewnelson.kmp.process.internal.node.node_fs
import io.matthewnelson.kmp.process.internal.node.node_process
import io.matthewnelson.kmp.process.internal.node.node_stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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
            options.dropAllInput()
            throw e
        }

        val opts = JsObject.new()

        // To fill after spawnSync completes
        var input: JsInt8Array? = null

        jsStdio.closeDescriptorsOnFailure {
            options.consumeInputBytes()?.let { b ->
                val a = b.toJsArray(factory = ::JsInt8Array)
                b.fill(0)
                input = a
                opts["input"] = a
            }
            options.consumeInputUtf8()?.let { utf8 ->
                opts["input"] = utf8
            }
        }

        chdir?.let { opts["cwd"] = it.path }
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
                    val argsArray = args.toJsArray()
                    jsExternTryCatch { m.spawnSync(command, argsArray, opts) }
                }
            } finally {
                input?.fill()
            }
        }

        val pid = output.getInt("pid")

        val processError: String? = output.getJsErrorOrNull("error").let { e ->
            if (e == null) return@let null
            // Spawn failure.
            if (pid <= 0) throw e.toThrowable().toIOException(command.toFile(), other = null)
            e.message
        }

        val stdout = output.getJsBufferOrNull("stdout").toBufferedOutput()
        val stderr = output.getJsBufferOrNull("stderr").toBufferedOutput()

        val code: Int = output.getIntOrNull("status").let { status ->
            if (status != null) return@let status

            val signal = output.getStringOrNull("signal")
            try {
                Signal.valueOf(signal!!)
            } catch (_: Throwable) {
                destroy
            }.code
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

    @Throws(CancellationException::class, IOException::class)
    internal actual suspend fun spawnAsync(
        fs: AsyncFs,
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
        isOutput: Boolean,
    ): Process = withContext(fs.ctx) {
        val p = spawn(
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
            handler,
            isAsync = true,
            isDetached = if (isOutput) false else detached,
            shell = shell,
            windowsVerbatimArguments = windowsVerbatimArguments,
            windowsHide = windowsHide,
            _close = { fd ->
                // non-cancellable
                @Suppress("SuspendCoroutineLacksCancellationGuarantees")
                suspendCoroutine { cont ->
                    close(fd) { err ->
                        if (err != null) {
                            val e = err.toThrowable().toIOException()
                            cont.resumeWithException(e)
                        } else {
                            cont.resume(Unit)
                        }
                    }
                }
            },
            _fstat = { fd ->
                suspendCancellableCoroutine { cont ->
                    fstat(fd) { err, stats ->
                        if (err != null) {
                            val e = err.toThrowable().toIOException()
                            cont.resumeWithException(e)
                        } else {
                            cont.resume(stats!!)
                        }
                    }
                }
            },
            _open = { path, flags ->
                suspendCancellableCoroutine { cont ->
                    open(path, flags) { err, fd ->
                        if (err != null) {
                            val e = err.toThrowable().toIOException(path.toFile())
                            cont.resumeWithException(e)
                        } else {
                            cont.resume(fd!!)
                        }
                    }
                }
            },
            _isCanonicallyEqualTo = { other -> isCanonicallyEqualTo(other, fs) },
        )

        // Dispatchers.Default is used here, which under the hood is NodeDispatcher,
        // in order for yield to work as intended in the event Process.Builder.async
        // was configured with a CoroutineContext that does not include a proper
        // dispatcher.
        withContext(NonCancellable + Dispatchers.Default) {
            while (p.pid() <= 0) {
                p.spawnError?.let { e ->
                    try {
                        p.destroy()
                    } catch (t: Throwable) {
                        e.addSuppressed(t)
                    }
                    throw e
                }

                // Yield should normally not be used to wait for things, but in this specific
                // use-case, it is OK because Js/WasmJs yield dispatches via process.nextTick
                // which we need here to allow the jsProcess.onError listener to dispatch its
                // spawn error (if there is one) so we can catch it.
                yield()
            }
        }

        p
    }

    // Deprecated
    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process = spawn(
        command,
        args,
        chdir,
        env,
        stdio,
        destroy,
        handler,
        isAsync = false,
        isDetached = detached,
        shell = shell,
        windowsVerbatimArguments = windowsVerbatimArguments,
        windowsHide = windowsHide,
    )

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()
    }
}

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

private fun JsBuffer?.toBufferedOutput(): Output.Buffered {
    if (this == null) return OutputFeedBuffer.EMPTY_OUTPUT
    val buf = this.asBuffer()

    var len = buf.length.toInt()
    while (len > 1) {
        if (buf.readInt8(len - 1) != LF) break
        len--
    }
    if (len <= 0) return OutputFeedBuffer.EMPTY_OUTPUT

    return object : Output.Buffered(length = len) {
        private val buffer = buf
        override fun get(index: Int): Byte = buffer[index]
        override fun iterator(): ByteIterator = object : ByteIterator() {
            private var i = 0
            override fun hasNext(): Boolean = i < length
            override fun nextByte(): Byte {
                if (i >= length) throw NoSuchElementException("Index $i out of bounds for length $length")
                return buffer[i++]
            }
        }
        override fun utf8(): String = _utf8
        private val _utf8: String by lazy { buffer.toUtf8(end = length) }
    }
}

@OptIn(ExperimentalContracts::class)
@Throws(IOException::class, UnsupportedOperationException::class)
private inline fun spawn(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
    handler: ProcessException.Handler,
    isAsync: Boolean,
    isDetached: Boolean,
    shell: Any,
    windowsVerbatimArguments: Boolean,
    windowsHide: Boolean,
    _close: ModuleFs.(Double) -> Unit = { fd -> jsExternTryCatch { closeSync(fd) } },
    _fstat: ModuleFs.(Double) -> JsStats = { fd -> jsExternTryCatch { fstatSync(fd) } },
    _open: ModuleFs.(String, String) -> Double = { path, flags -> jsExternTryCatch { openSync(path, flags) } },
    _isCanonicallyEqualTo: File.(File) -> Boolean = File::isCanonicallyEqualTo,
): NodeJsProcess {
    contract {
        callsInPlace(_close, InvocationKind.UNKNOWN)
        callsInPlace(_fstat, InvocationKind.UNKNOWN)
        callsInPlace(_open, InvocationKind.UNKNOWN)
        callsInPlace(_isCanonicallyEqualTo, InvocationKind.UNKNOWN)
    }

    node_stream
    val jsStdio = stdio.toJsStdio(_close, _fstat, _isCanonicallyEqualTo, _open)

    val opts = JsObject.new()
    chdir?.let { opts["cwd"] = it.path }
    opts["env"] = env.toJsObject()
    opts["stdio"] = jsStdio.toJsArray()
    opts["detached"] = isDetached
    when (shell) {
        is String -> opts["shell"] = shell
        is Boolean -> opts["shell"] = shell
    }
    opts["windowsVerbatimArguments"] = windowsVerbatimArguments
    opts["windowsHide"] = windowsHide
    opts["killSignal"] = destroy.name

    val jsProcess = jsStdio.closeDescriptorsOnFailure(_close) {
        node_child_process.let { m ->
            val argsArray = args.toJsArray()
            jsExternTryCatch { m.spawn(command, argsArray, opts) }
        }
    }

    return NodeJsProcess(
        jsProcess,
        isAsync,
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

@OptIn(ExperimentalContracts::class)
@Throws(IOException::class, UnsupportedOperationException::class)
private inline fun Stdio.Config.toJsStdio(
    _close: ModuleFs.(Double) -> Unit = { fd -> jsExternTryCatch { closeSync(fd) } },
    _fstat: ModuleFs.(Double) -> JsStats = { fd -> jsExternTryCatch { fstatSync(fd) } },
    _isCanonicallyEqualTo: File.(File) -> Boolean = File::isCanonicallyEqualTo,
    _open: ModuleFs.(String, String) -> Double = { path, flags -> jsExternTryCatch { openSync(path, flags) } },
): List<Any> {
    contract {
        callsInPlace(_close, InvocationKind.UNKNOWN)
        callsInPlace(_fstat, InvocationKind.UNKNOWN)
        callsInPlace(_isCanonicallyEqualTo, InvocationKind.UNKNOWN)
        callsInPlace(_open, InvocationKind.UNKNOWN)
    }

    val fs = node_fs
    val jsStdio = ArrayList<Any>(3)
    jsStdio.closeDescriptorsOnFailure(_close) {
        stdin.toJsStdio(fs, isStdin = true, _close, _fstat, _open).let { jsStdio.add(it) }
        stdout.toJsStdio(fs, isStdin = false, _close, _fstat, _open).let { jsStdio.add(it) }
        if (isStderrSameFileAsStdout(_isCanonicallyEqualTo)) {
            // use the same file descriptor as stdout
            jsStdio[1]
        } else {
            stderr.toJsStdio(fs, isStdin = false, _close, _fstat, _open)
        }.let { jsStdio.add(it) }
    }
    return jsStdio
}

@Throws(Throwable::class)
@OptIn(ExperimentalContracts::class)
private inline fun Stdio.toJsStdio(
    fs: ModuleFs,
    isStdin: Boolean,
    _close: ModuleFs.(Double) -> Unit,
    _fstat: ModuleFs.(Double) -> JsStats,
    _open: ModuleFs.(String, String) -> Double,
): Any {
    contract {
        callsInPlace(_close, InvocationKind.AT_MOST_ONCE)
        callsInPlace(_fstat, InvocationKind.AT_MOST_ONCE)
        callsInPlace(_open, InvocationKind.AT_MOST_ONCE)
    }

    return when (this) {
        is Stdio.Inherit -> "inherit"
        is Stdio.Pipe -> "pipe"
        is Stdio.File -> when {
            file == STDIO_NULL -> "ignore"
            isStdin -> {
                val fd = fs._open(file.path, "r")

                try {
                    val isDirectory = fs._fstat(fd).isDirectory()
                    if (isDirectory) throw FileSystemException(file, null, "EISDIR: Is a Directory")
                } catch (t: Throwable) {
                    try {
                        fs._close(fd)
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    }
                    throw t
                }

                fd
            }

            append -> fs._open(file.path, "a")
            else -> fs._open(file.path, "w")
        }
    }
}

@Throws(CancellationException::class, IOException::class)
@OptIn(ExperimentalContracts::class)
private inline fun <T> List<Any>.closeDescriptorsOnFailure(
    _close: ModuleFs.(Double) -> Unit = { fd -> jsExternTryCatch { closeSync(fd) } },
    block: () -> T,
): T {
    contract {
        callsInPlace(_close, InvocationKind.UNKNOWN)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return try {
        block()
    } catch (t: Throwable) {
        val e = (t as? CancellationException) ?: t.toIOException()
        forEach { stdio ->
            val fd = stdio as? Double ?: return@forEach
            try {
                node_fs._close(fd)
            } catch (tt: Throwable) {
                e.addSuppressed(tt)
            }
        }
        throw e
    }
}
