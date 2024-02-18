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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.time.Duration

internal class NativeProcess
@Throws(IOException::class)
internal constructor(
    private val pid: Int,
    private val handle: StdioHandle,
    command: String,
    args: List<String>,
    env: Map<String, String>,
    destroy: Signal,
): Process(command, args, env, handle.stdio, destroy, INIT) {

    init {
        if (pid <= 0) {
            handle.close()
            throw IOException("pid[$pid] must be greater than 0")
        }
    }

    private val _exitCode = AtomicReference<Int?>(null)

    override fun destroy(): Process {
        isDestroyed = true

        if (isAlive) {
            val s = when (destroySignal) {
                Signal.SIGTERM -> SIGTERM
                Signal.SIGKILL -> SIGKILL
            }

            kill(pid, s)

            // TODO: https://man7.org/linux/man-pages/man7/signal.7.html
            // TODO, wait
        }

        handle.close()

        return this
    }

    @Throws(IllegalStateException::class)
    override fun exitCode(): Int {
        _exitCode.value?.let { return it }

        @OptIn(ExperimentalForeignApi::class)
        memScoped {
            val statLoc = alloc<IntVar>()

            when (waitpid(pid, statLoc.ptr, WNOHANG or WUNTRACED)) {
                0 -> { /* unavailable status */ }
                pid -> {
                    val code = statLoc.value shr 8 and 0x000000FF
                    _exitCode.compareAndSet(null, code)
                }
                else -> {
                    val message = strerror(errno)?.toKString() ?: "errno: $errno"
                    throw IllegalStateException(message)
                }
            }
        }

        return _exitCode.value ?: throw IllegalStateException("Process hasn't exited")
    }

    override fun pid(): Int = pid

    override fun waitFor(): Int {
        var exitCode: Int? = null
        while (exitCode == null) {
            exitCode = waitFor(Duration.INFINITE)
        }
        return exitCode
    }

    override fun waitFor(duration: Duration): Int? = commonWaitFor(duration) { it.threadSleep() }

    override fun startStdout() {
        // TODO
    }

    override fun startStderr() {
        // TODO
    }
}
