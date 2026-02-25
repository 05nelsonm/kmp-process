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
@file:Suppress("PropertyName", "RedundantVisibilityModifier")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errorCodeOrNull
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.process.AsyncWriteStream
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.node.node_process
import io.matthewnelson.kmp.process.internal.js.getString
import io.matthewnelson.kmp.process.internal.node.JsChildProcess
import io.matthewnelson.kmp.process.internal.node.onClose
import io.matthewnelson.kmp.process.internal.node.onData
import io.matthewnelson.kmp.process.internal.node.onError

internal class NodeJsProcess internal constructor(
    private val jsProcess: JsChildProcess,
    isAsync: Boolean,
    private val isDetached: Boolean,
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
    handler: ProcessException.Handler,
): Process(
    command,
    args,
    chdir,
    env,
    stdio,
    jsProcess.stdin?.let { AsyncWriteStream(it) },
    destroy,
    handler,
    INIT,
) {

    private var _exitCode: Int? = null
    internal var _hasStdoutStarted: Boolean = false
        private set
    internal var _hasStderrStarted: Boolean = false
        private set
    internal var spawnError: IOException? = null
        private set

    init {
        jsProcess.onError { t ->
            if (isDestroyed) return@onError
            if (pid() <= 0) {
                val e = t.toIOException(command.toFile())
                if (isAsync) {
                    // Hoist out of onError listener lambda so that
                    // PlatformBuilder.spawnAsync can pick it up to
                    // throw it.
                    spawnError = e
                    return@onError
                }
                e
            } else {
                t.toIOException()
            }.let { onError(it, context = ERROR_CONTEXT) }
        }

        if (isDetached) jsProcess.unref()
    }

    @Throws(Throwable::class)
    protected override fun destroyProtected(immediate: Boolean) {
        val wasDestroyed = !isDestroyed
        isDestroyed = true

        @Suppress("UNUSED_VARIABLE")
        val error: Throwable? = if (!jsProcess.killed && isAlive) {
            try {
                jsExternTryCatch { jsProcess.kill(destroySignal.name) }
                null
            } catch (t: Throwable) {
                val code = t.errorCodeOrNull
                var destroySelfIfAlive = false

                // https://github.com/05nelsonm/kmp-process/issues/108
                run {
                    if (!IsWindows) return@run
                    if (code != "EPERM") return@run

                    val (major, minor, patch) = try {
                        val split = node_process.versions.getString("uv").split('.')
                        Triple(split[0].toInt(), split[1].toInt(), split[2].toInt())
                    } catch (_: Throwable) {
                        return@run
                    }

                    // libuv 1.48.0 bad windows implementation
                    if (major == 1 && minor == 48 && patch == 0) {
                        destroySelfIfAlive = true
                    }
                }

                if (code == "ESRCH") {
                    destroySelfIfAlive = true
                }

                var error: Throwable? = t

                if (destroySelfIfAlive && isAlive) {
                    // Still registering as "alive" w/o exit code, but no process
                    // found with our PID. Unable to kill b/c no process, it's just
                    // gone... Self-assign exit code and move on.
                    _exitCode = destroySignal.code
                    try {
                        jsExternTryCatch { jsProcess.stdin?.end() }
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    }
                    try {
                        jsExternTryCatch { jsProcess.stdout?.destroy() }
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    }
                    try {
                        jsExternTryCatch { jsProcess.stderr?.destroy() }
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    }
                    error = null
                }

                error
            }
        } else {
            null
        }

        if (wasDestroyed && !isDetached) jsProcess.unref()

        error?.let { throw it }
    }

    public override fun exitCodeOrNull(): Int? {
        _exitCode?.let { return it }

        jsProcess.exitCode?.let {
            _exitCode = it
            return it
        }

        jsProcess.signalCode?.let { signal ->
            val code = try {
                Signal.valueOf(signal)
            } catch (_: IllegalArgumentException) {
                destroySignal
            }.code

            _exitCode = code
            return code
        }

        return _exitCode
    }

    public override fun pid(): Int {
        val result = try {
            // can be undefined if called before the
            // underlying process has not spawned yet.
            jsProcess.pid
        } catch (_: Throwable) {
            null
        }

        return result ?: -1
    }

    protected override fun startStdout() {
        val stdout = jsProcess.stdout ?: return
        val dispatch = dispatchStdoutRef()
        stdout.onClose { dispatch(null, -1) }.onData { data -> dispatch(data, data.size()) }
        _hasStdoutStarted = true
    }

    protected override fun startStderr() {
        val stderr = jsProcess.stderr ?: return
        val dispatch = dispatchStderrRef()
        stderr.onClose { dispatch(null, -1) }.onData { data -> dispatch(data, data.size()) }
        _hasStderrStarted = true
    }

    private companion object {
        private const val ERROR_CONTEXT = "nodejs.on('error')"
    }
}
