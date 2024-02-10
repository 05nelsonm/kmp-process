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
): Process(command, args, env, stdio, destroy) {

    override fun destroy(): Process {
        if (!jsProcess.killed && isAlive) {
            // TODO: check result. check error
            jsProcess.kill(destroySignal.name)
        }
        return this
    }

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

    override fun pid(): Int = jsProcess.pid?.toInt() ?: -1

    override fun waitFor(): Int = throw UnsupportedOperationException(WAIT_FOR_ERR)
    override fun waitFor(duration: Duration): Int = throw UnsupportedOperationException(WAIT_FOR_ERR)

    private companion object {

        private const val WAIT_FOR_ERR =
            "waitFor is not supported on Node.js. " +
            "Use waitForAsync or Process.Builder.output()"
    }
}
