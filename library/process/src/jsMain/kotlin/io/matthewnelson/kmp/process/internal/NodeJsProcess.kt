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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.errorCodeOrNull
import io.matthewnelson.kmp.process.*

internal class NodeJsProcess internal constructor(
    private val jsProcess: child_process_ChildProcess,
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): Process(
    command,
    args,
    chdir,
    env,
    stdio,
    jsProcess.stdin?.let { AsyncWriteStream(it) },
    destroy,
    INIT,
) {

    private var _exitCode: Int? = null

    override fun destroy(): Process {
        val wasDestroyed = !isDestroyed
        isDestroyed = true

        @Suppress("UNUSED_VARIABLE")
        val error: Throwable? = if (!jsProcess.killed && isAlive) {
            try {
                jsProcess.kill(destroySignal.name)
                null
            } catch (t: Throwable) {
                val code = t.errorCodeOrNull
                var destroySelfIfAlive = false

                // https://github.com/05nelsonm/kmp-process/issues/108
                run {
                    if (!IsWindows) return@run
                    if (code != "EPERM") return@run

                    val (major, minor, patch) = try {
                        val split = (process_versions["uv"] as String).split('.')
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
                    jsProcess.stdin?.end()
                    jsProcess.stdout?.destroy()
                    jsProcess.stderr?.destroy()
                    error = null
                }

                error
            }
        } else {
            null
        }

        if (wasDestroyed) jsProcess.unref()

        // TODO: Handle errors Issue #109

        return this
    }

    override fun exitCodeOrNull(): Int? {
        _exitCode?.let { return it }

        jsProcess.exitCode?.toInt()?.let {
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

    override fun pid(): Int {
        val result = try {
            // can be undefined if called before the
            // underlying process has not spawned yet.
            jsProcess.pid?.toInt()
        } catch (_: Throwable) {
            null
        }

        return result ?: -1
    }

    override fun startStdout() {
        val stdout = jsProcess.stdout ?: return

        @OptIn(InternalProcessApi::class)
        ReadBuffer.lineOutputFeed(::dispatchStdout).apply {
            stdout.onClose {
                close()
            }.onData { data ->
                onData(data, data.capacity())
            }
        }
    }

    override fun startStderr() {
        val stderr = jsProcess.stderr ?: return

        @OptIn(InternalProcessApi::class)
        ReadBuffer.lineOutputFeed(::dispatchStderr).apply {
            stderr.onClose {
                close()
            }.onData { data ->
                onData(data, data.capacity())
            }
        }
    }
}
