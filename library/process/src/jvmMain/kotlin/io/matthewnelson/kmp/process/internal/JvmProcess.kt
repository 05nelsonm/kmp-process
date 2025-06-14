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
@file:Suppress("RemoveRedundantQualifierName", "RedundantVisibilityModifier")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.process.*
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.concurrent.Volatile

internal class JvmProcess private constructor(
    private val jProcess: java.lang.Process,
    isStderrRedirectedToStdout: Boolean,
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
    if (stdio.stdin is Stdio.Pipe) {
        AsyncWriteStream.of(jProcess.outputStream)
    } else {
        null
    },
    destroy,
    handler,
    INIT,
) {

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

    @Volatile
    @get:JvmName("wasStdoutThreadStarted")
    internal var wasStdoutThreadStarted: Boolean = false
        private set
    @Volatile
    @get:JvmName("wasStderrThreadStarted")
    internal var wasStderrThreadStarted: Boolean = false
        private set

    // @Throws(Throwable::class)
    protected override fun destroyProtected(immediate: Boolean) {
        isDestroyed = true

        @Suppress("NewApi")
        when {
            // java.lang.Process.destroyForcibly on Android
            // (when available) does absolutely fuck all but
            // call destroy under the hood. Very sad that there
            // is no choice in the termination signal which is
            // what destroyForcibly was intended for.
            ANDROID.SDK_INT != null -> jProcess.destroy()

            else -> when (destroySignal) {
                Signal.SIGTERM -> jProcess.destroy()
                Signal.SIGKILL -> jProcess.destroyForcibly()
            }
        }

        _stdinThread?.let { thread ->
            _stdinThread = null
            if (thread.isInterrupted) return@let
            thread.interrupt()
        }
    }

    public override fun exitCodeOrNull(): Int? = try {
        jProcess.exitValue().correctExitCode()
    } catch (_: IllegalThreadStateException) {
        null
    }

    public override fun pid(): Int = _pid

    protected override fun startStdout() {
        Runnable {
            wasStdoutThreadStarted = true
            jProcess.inputStream.scanLines(::dispatchStdout)
        }.execute(stdio = "stdout")
    }

    protected override fun startStderr() {
        Runnable {
            wasStderrThreadStarted = true
            jProcess.errorStream.scanLines(::dispatchStderr)
        }.execute(stdio = "stderr")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Runnable.execute(stdio: String): Thread {
        val t = Thread(this, "Process[pid=$_pid, stdio=$stdio]")
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
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

        ANDROID.SDK_INT?.let { sdkInt ->
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
        if (stdio.stdin is Stdio.File && stdio.stdin.file == STDIO_NULL) {
            try {
                jProcess.outputStream.close()
            } catch (_: Throwable) {}
        }

        ANDROID.SDK_INT?.let { sdkInt ->
            if (sdkInt >= 24) return@let

            // Android API 23 and below does not have redirect
            // capabilities. Below is a supplemental implementation

            fun ReadStream.writeTo(oStream: WriteStream) {
                val iStream = this
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    val read = iStream.read(buf)
                    if (read == -1) break
                    oStream.write(buf, 0, read)
                }

                buf.fill(0)
            }

            when (val s = stdio.stdin) {
                is Stdio.File -> if (s.file != STDIO_NULL) {
                    _stdinThread = Runnable {
                        try {
                            s.file.inputStream().use { iStream ->
                                jProcess.outputStream.use { oStream ->
                                    iStream.writeTo(oStream)
                                }
                            }
                        } catch (_: Throwable) {}
                    }.execute(stdio = "stdin-redirect")
                }
                is Stdio.Inherit -> {
                    // TODO: Need to think about...
                }
                is Stdio.Pipe -> { /* do nothing */ }
            }

            fun ReadStream.redirectTo(stdio: String, file: Stdio.File) {
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

            fun ReadStream.redirectTo(stdio: String, oStream: PrintStream) {
                Runnable {
                    try {
                        use { iStream ->
                            iStream.writeTo(oStream)
                        }
                    } catch (_: Throwable) {}
                }.execute(stdio = stdio)
            }

            when (val o = stdio.stdout) {
                is Stdio.File -> jProcess.inputStream.redirectTo("stdout-redirect", o)
                is Stdio.Inherit -> jProcess.inputStream.redirectTo("stdout-redirect", System.out)
                is Stdio.Pipe -> { /* do nothing */ }
            }

            if (!isStderrRedirectedToStdout) {
                when (val o = stdio.stderr) {
                    is Stdio.File -> jProcess.errorStream.redirectTo("stderr-redirect", o)
                    is Stdio.Inherit -> jProcess.errorStream.redirectTo("stderr-redirect", System.err)
                    is Stdio.Pipe -> { /* do nothing */ }
                }
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            jProcess: java.lang.Process,
            isStderrRedirectedToStdout: Boolean,
            command: String,
            args: List<String>,
            chdir: File?,
            env: Map<String, String>,
            stdio: Stdio.Config,
            destroy: Signal,
            handler: ProcessException.Handler,
        ): JvmProcess = JvmProcess(
            jProcess,
            isStderrRedirectedToStdout,
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
            handler,
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
