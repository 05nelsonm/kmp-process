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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.AsyncWriteStream
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.internal.Closeable.Companion.tryCloseSuppressed
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

@OptIn(ObsoleteWorkersApi::class)
internal class NativeProcess
@Throws(IOException::class)
internal constructor(
    private val pid: Int,
    private val handle: StdioHandle,
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    destroy: Signal,
): Process(
    command,
    args,
    chdir,
    env,
    handle.stdio,
    handle.stdinStream()?.let { AsyncWriteStream.of(it) },
    destroy,
    INIT,
) {

    init {
        if (pid <= 0) {
            val t = IOException("pid[$pid] must be greater than 0")
            handle.tryCloseSuppressed(t)
            throw t
        }
    }

    private val destroyLock = Lock()
    private val _exitCode = AtomicReference<Int?>(null)

    private val stdoutWorker = Instance(create = {
        if (isDestroyed) return@Instance null
        val reader = handle.stdoutReader() ?: return@Instance null

        Worker.start("stdout", reader, ::dispatchStdout, ::onStdoutStopped)
    })

    private val stderrWorker = Instance(create = {
        if (isDestroyed) return@Instance null
        val reader = handle.stderrReader() ?: return@Instance null

        Worker.start("stderr", reader, ::dispatchStderr, ::onStderrStopped)
    })

    override fun destroy(): Process = destroyLock.withLock {
        val hasBeenDestroyed = isDestroyed
        isDestroyed = true

        if (isAlive) {
            val s = when (destroySignal) {
                Signal.SIGTERM -> SIGTERM
                Signal.SIGKILL -> SIGKILL
            }

            kill(pid, s)

            isAlive
        }

        try {
            handle.close()
        } catch (_: IOException) {
            // TODO: Error handler
        }

        if (!hasBeenDestroyed) {
            stdoutWorker
                .getOrNull()
                ?.requestTermination(processScheduledJobs = false)
                ?.result

            stderrWorker
                .getOrNull()
                ?.requestTermination(processScheduledJobs = false)
                ?.result
        }

        this
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
                    var code = statLoc.value

                    if (code != 0) {
                        val status = code shr 8 and 0x000000FF

                        if (status != 0) {
                            // exited with a non-zero value, e.g. exit 42
                            code = status
                        } else {
                            // signal stopped the process
                            code += 128
                        }
                    }

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

    override fun startStdout() { stdoutWorker.getOrCreate() }
    override fun startStderr() { stderrWorker.getOrCreate() }

    private fun Worker.Companion.start(
        name: String,
        r: ReadStream,
        d: (line: String?) -> Unit,
        s: () -> Unit,
    ): Worker {
        val w = start(name = "Process[pid=$pid, stdio=$name]")

        w.execute(TransferMode.SAFE, { Triple(r, d, s) }) { (reader, dispatch, onStopped) ->
            reader.scanLines(dispatch, onStopped)
        }

        return w
    }
}
