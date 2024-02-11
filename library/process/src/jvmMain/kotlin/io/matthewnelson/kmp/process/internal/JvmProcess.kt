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

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.time.Duration

internal class JvmProcess private constructor(
    private val jProcess: java.lang.Process,
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): Process(command, args, env, stdio, destroy) {

    private val _pid: Int by lazy {
        // First try parsing toString output
        jProcess.toString()
            // Process[pid=1754008, exitValue="not exited"]
            .substringAfter("pid=")
            .substringBefore(']')
            .substringBefore(',')
            // "Bug" in Android may add a space after the pid value
            // before the comma if there are more than 2 arguments.
            .trim()
            .toIntOrNull()
            ?.let { return@lazy it }

        // Lastly try reflection
        try {
            val id = java.lang.Process::class.java
                .getDeclaredMethod("pid")
                .invoke(jProcess) as Long

            return@lazy id.toInt()
        } catch (_: Throwable) {}

        // Unknown
        -1
    }

    @Volatile
    private var destroyCalled = false

    override fun destroy(): Process {
        destroyCalled = true

        when (destroySignal) {
            Signal.SIGTERM -> jProcess.destroy()
            Signal.SIGKILL -> jProcess.destroyForcibly()
        }

        return this
    }

    @Throws(IllegalStateException::class)
    override fun exitCode(): Int {
        var result: Int? = try {
            jProcess.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }

        // On Windows it's either 0 or 1, 1 indicating
        // termination. Swap it out with the correct code
        if (result != null && destroyCalled && STDIO_NULL.path == "NUL") {
            if (result == 1) result = destroySignal.code
        }

        return result ?: throw IllegalStateException("Process hasn't exited")
    }

    override fun pid(): Int = _pid

    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        var code = jProcess.waitFor()

        // non-zero exit value, check exitCode() to see if
        // it was a windows termination thing.
        if (code == 1) {
           code = try {
               exitCode()
           } catch (_: IllegalStateException) {
               code
           }
        }

        return code
    }

    @Throws(InterruptedException::class)
    override fun waitFor(duration: Duration): Int? = commonWaitFor(duration) { Thread.sleep(it.inWholeMilliseconds) }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            jProcess: java.lang.Process,
            command: String,
            args: List<String>,
            env: Map<String, String>,
            stdio: Stdio.Config,
            destroy: Signal,
        ): JvmProcess = JvmProcess(
            jProcess,
            command,
            args,
            env,
            stdio,
            destroy,
        )
    }
}
