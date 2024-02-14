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

import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.time.Duration

internal class NodeJsProcess internal constructor(
    private val jsProcess: child_process_ChildProcess,
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): Process(command, args, env, stdio, destroy, SyntheticAccess.get()) {

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

    // @Throws(UnsupportedOperationException::class)
    override fun waitFor(): Int = throw UnsupportedOperationException(WAIT_FOR_ERR)
    // @Throws(UnsupportedOperationException::class)
    override fun waitFor(duration: Duration): Int = throw UnsupportedOperationException(WAIT_FOR_ERR)

    override fun startStdout() {
        jsProcess.stdout
            ?.onClose(::onStdoutStopped)
            ?.onData { data ->
                data.dispatchLinesTo(::dispatchStdout)
            }
    }

    override fun startStderr() {
        jsProcess.stderr
            ?.onClose(::onStderrStopped)
            ?.onData { data ->
                data.dispatchLinesTo(::dispatchStderr)
            }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun String.dispatchLinesTo(
        dispatch: (line: String) -> Unit,
    ) {
        val lines = lines()
        val iLast = lines.lastIndex
        for (i in lines.indices) {
            val line = lines[i]
            if (i == iLast && line.isEmpty()) {
                // If data ended with a return, skip it
                continue
            } else {
                dispatch(line)
            }
        }
    }

    private companion object {

        private const val WAIT_FOR_ERR =
            "waitFor is not supported on Node.js. " +
            "Use waitForAsync or Process.Builder.output()"
    }
}
