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
import io.matthewnelson.kmp.process.internal.BufferedLineScanner.Companion.scanLines
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.PlatformBuilder.Companion.ANDROID_SDK_INT
import java.io.InputStream
import kotlin.time.Duration

internal class JvmProcess private constructor(
    private val jProcess: java.lang.Process,
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): Process(command, args, env, stdio, destroy, INIT) {

    private val _pid: Int by lazy {
        // First try parsing toString output
        jProcess.toString()
            // Process[pid=1754008]
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
        PidMethod?.let { method ->
            try {
                val id = method.invoke(jProcess) as Long
                return@lazy id.toInt()
            } catch (_: Throwable) {}
        }

        // Unknown
        -1
    }

    override fun destroy(): Process {
        isDestroyed = true

        @Suppress("NewApi")
        when (destroySignal) {
            Signal.SIGTERM -> jProcess.destroy()
            Signal.SIGKILL -> ANDROID_SDK_INT?.let { sdkInt ->
                if (sdkInt >= 26) {
                    jProcess.destroyForcibly()
                } else {
                    jProcess.destroy()
                }
            } ?: jProcess.destroyForcibly()
        }

        return this
    }

    @Throws(IllegalStateException::class)
    override fun exitCode(): Int = try {
        jProcess.exitValue().correctExitCode()
    } catch (_: IllegalThreadStateException) {
        throw IllegalStateException("Process hasn't exited")
    }

    override fun pid(): Int = _pid

    @Throws(InterruptedException::class)
    override fun waitFor(): Int = jProcess.waitFor().correctExitCode()

    @Throws(InterruptedException::class)
    override fun waitFor(duration: Duration): Int? = commonWaitFor(duration) { it.threadSleep() }

    override fun startStdout() {
        StreamEater(
            jProcess.inputStream,
            ::dispatchStdout,
            ::onStdoutStopped,
        ).start("stdout")
    }

    override fun startStderr() {
        StreamEater(
            jProcess.errorStream,
            ::dispatchStderr,
            ::onStderrStopped,
        ).start("stderr")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun StreamEater.start(name: String) {
        val t = Thread(this, "Process[pid=$_pid, stdio=$name]")
        t.isDaemon = true
        try {
            t.start()
        } catch (_: IllegalThreadStateException) {}
    }

    private class StreamEater(
        private val stream: InputStream,
        private val dispatch: (line: String) -> Unit,
        private val onStopped: () -> Unit,
    ): Runnable {
        override fun run() {
            stream.scanLines(dispatch, onStopped)
        }
    }

    private fun Int.correctExitCode(): Int {
        if (!isDestroyed) return this

        // Process.destroy was invoked

        ANDROID_SDK_INT?.let { sdkInt ->
            // Android 23 and below uses SIGKILL when
            // destroy is invoked, but fails to add 128
            // the value like newer versions do.
            return if (sdkInt < 24 && this == 9) {
                this + 128
            } else {
                this
            }
        }

        // On Windows it's either 0 or 1, 1 indicating termination. Swap
        // it out with the correct code if destroy was called
        if (this == 1 && IsWindows) {
            return destroySignal.code
        }

        return this
    }

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

        private val PidMethod by lazy {
            try {
                java.lang.Process::class.java
                    .getDeclaredMethod("pid")
            } catch (_: Throwable) {
                null
            }
        }
    }
}
