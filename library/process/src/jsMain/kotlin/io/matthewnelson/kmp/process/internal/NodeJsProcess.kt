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
import io.matthewnelson.kmp.process.AsyncWriteStream
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

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

    override fun destroy(): Process {
        isDestroyed = true

        if (!jsProcess.killed && isAlive) {
            // TODO: check result. check error
            jsProcess.kill(destroySignal.name)
        }
        return this
    }

    // @Throws(IllegalStateException::class)
    override fun exitCode(): Int {
        jsProcess.exitCode?.toInt()?.let { return it }

        jsProcess.signalCode?.let { signal ->
            return try {
                Signal.valueOf(signal)
            } catch (_: IllegalArgumentException) {
                destroySignal
            }.code
        }

        throw IllegalStateException("Process hasn't exited")
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

        object : BufferedLineScanner(::dispatchStdout) {
            init {
                stdout.onClose {
                    onStopped()
                    onStdoutStopped()
                }.onData { data ->
                    onData(data)
                    data.fill(0)
                }
            }
        }
    }

    override fun startStderr() {
        val stderr = jsProcess.stderr ?: return

        object : BufferedLineScanner(::dispatchStderr) {
            init {
                stderr.onClose {
                    onStopped()
                    onStderrStopped()
                }.onData { data ->
                    onData(data)
                    data.fill(0)
                }
            }
        }
    }
}
