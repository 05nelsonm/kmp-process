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
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.concurrent.Volatile
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

    @Volatile
    private var _exitCode: Int? = null
    @Volatile
    private var _stdinThread: Thread? = null

    override fun destroy(): Process {
        isDestroyed = true

        @Suppress("NewApi")
        when {
            // java.lang.Process.destroyForcibly on Android
            // (when available) does absolutely fuck all but
            // call destroy under the hood. Very sad that there
            // is no choice in the termination signal which is
            // what destroyForcibly was intended for.
            ANDROID_SDK_INT != null -> jProcess.destroy()

            else -> when (destroySignal) {
                Signal.SIGTERM -> jProcess.destroy()
                Signal.SIGKILL -> jProcess.destroyForcibly()
            }
        }

        _stdinThread?.let { thread ->
            if (thread.isInterrupted) return@let
            thread.interrupt()
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
        Runnable {
            jProcess.inputStream.scanLines(::dispatchStdout, ::onStdoutStopped)
        }.execute(stdio = "stdout")
    }

    override fun startStderr() {
        Runnable {
            jProcess.errorStream.scanLines(::dispatchStderr, ::onStderrStopped)
        }.execute(stdio = "stderr")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Runnable.execute(stdio: String): Thread {
        val t = Thread(this, "Process[pid=$_pid, stdio=$stdio]")
        t.isDaemon = true
        t.start()
        return t
    }

    private fun Int.correctExitCode(): Int {
        _exitCode?.let { return it }

        if (!isDestroyed) {
            // Want to preserve the status code
            // if destroy was not invoked yet and
            // the program exited
            _exitCode = this
            return this
        }

        // Process.destroy was invoked

        ANDROID_SDK_INT?.let { sdkInt ->
            // Android 23 and below uses SIGKILL when
            // destroy is invoked, but fails to add 128
            // the value like newer versions do.
            val code = if (sdkInt < 24 && this == 9) {
                this + 128
            } else {
                this
            }

            _exitCode = code
            return code
        }

        // On Windows it's either 0 or 1, 1 indicating termination. Swap
        // it out with the correct code if destroy was called
        if (this == 1 && IsWindows) {
            _exitCode = destroySignal.code
            return destroySignal.code
        }

        _exitCode = this
        return this
    }

    init {
        ANDROID_SDK_INT?.let { sdkInt ->
            if (sdkInt >= 24) return@let

            // Android API 23 and below does not have redirect
            // capabilities. Below is a supplemental implementation

            fun InputStream.writeTo(oStream: OutputStream) {
                val iStream = this
                val buf = ByteArray(4096)

                while (true) {
                    val read = iStream.read(buf)
                    if (read == -1) break
                    oStream.write(buf, 0, read)
                }

                buf.fill(0)
            }

            when (val s = stdio.stdin) {
                is Stdio.File -> {
                    if (s.file == STDIO_NULL) {
                        try {
                            jProcess.outputStream.close()
                        } catch (_: Throwable) {}
                    } else {
                        _stdinThread = Runnable {
                            try {
                                s.file.inputStream().use { iStream ->
                                    jProcess.outputStream.use { oStream ->
                                        iStream.writeTo(oStream)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }.execute(stdio = "stdin")
                    }
                }
                is Stdio.Inherit -> {
                    // TODO: Need to think about...
                }
                is Stdio.Pipe -> { /* do nothing */ }
            }

            fun InputStream.redirectTo(stdio: String, file: Stdio.File) {
                Runnable {
                    try {
                        use { iStream ->
                            FileOutputStream(file.file, file.append).use { oStream ->
                                iStream.writeTo(oStream)
                            }
                        }
                    } catch (_: Throwable) {}
                }.execute(stdio = stdio)
            }

            fun InputStream.redirectTo(stdio: String, oStream: PrintStream) {
                Runnable {
                    try {
                        use { iStream ->
                            iStream.writeTo(oStream)
                        }
                    } catch (_: Throwable) {}
                }.execute(stdio = stdio)
            }

            when (val o = stdio.stdout) {
                is Stdio.File -> jProcess.inputStream.redirectTo("stdout", o)
                is Stdio.Inherit -> jProcess.inputStream.redirectTo("stdout", System.out)
                is Stdio.Pipe -> { /* do nothing */ }
            }

            when (val o = stdio.stderr) {
                is Stdio.File -> jProcess.errorStream.redirectTo("stderr", o)
                is Stdio.Inherit -> jProcess.errorStream.redirectTo("stderr", System.err)
                is Stdio.Pipe -> { /* do nothing */ }
            }
        }
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
