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
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.AsyncWriteStream
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.time.Duration.Companion.milliseconds

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
    handler: ProcessException.Handler,
): Process(
    command,
    args,
    chdir,
    env,
    handle.stdio,
    handle.stdinStream()?.let { AsyncWriteStream.of(it) },
    destroy,
    handler,
    INIT,
) {

    init {
        if (pid <= 0) {
            throw handle.tryCloseSuppressed(IOException("pid[$pid] must be greater than 0"))
        }
    }

    private val destroyLock = newLock()
    private val _exitCode = AtomicReference<Int?>(null)

    private val stdoutWorker = Instance(create = {
        if (isDestroyed) return@Instance null
        val reader = handle.stdoutReader() ?: return@Instance null

        Worker.execute("stdout", reader, ::dispatchStdout)
    })

    private val stderrWorker = Instance(create = {
        if (isDestroyed) return@Instance null
        val reader = handle.stderrReader() ?: return@Instance null

        Worker.execute("stderr", reader, ::dispatchStderr)
    })

    @Throws(Throwable::class)
    protected override fun destroyProtected(immediate: Boolean) = destroyLock.withLock {
        val hasBeenDestroyed = isDestroyed
        isDestroyed = true

        var threw: Throwable? = null

        if (isAlive) {
            val sig = when (destroySignal) {
                Signal.SIGTERM -> SIGTERM
                Signal.SIGKILL -> SIGKILL
            }

            var errno = if (kill(pid, sig) == -1) {
                errno
            } else {
                null
            }

            // Always call isAlive whether there was an error
            // or not to ensure _exitCode is set.
            if (!isAlive) {
                errno = null
            }

            if (errno != null) {
                @OptIn(ExperimentalForeignApi::class)
                threw = errnoToIOException(errno)
            }
        }

        try {
            handle.close()
        } catch (t: IOException) {
            if (threw != null) {
                threw.addSuppressed(t)
            } else {
                threw = t
            }
        }

        val terminate: (() -> Unit)? = run {
            if (hasBeenDestroyed) return@run null

            val wStdout = stdoutWorker.getOrNull()
            val wStderr = stderrWorker.getOrNull()

            if (wStdout == null && wStderr == null) return@run null

            {
                wStdout?.requestTermination(processScheduledJobs = false)?.result
                wStderr?.requestTermination(processScheduledJobs = false)?.result
            }
        }

        Pair(threw, terminate)
    }.let { (threw, terminate) ->
        if (terminate != null) {
            if (immediate) {
                terminate()
            } else {
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    delay(150.milliseconds)
                    terminate()
                }
            }
        }

        if (threw != null) throw threw
    }

    public override fun exitCodeOrNull(): Int? {
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
                else -> {}
            }
        }

        return _exitCode.value
    }

    public override fun pid(): Int = pid

    protected override fun startStdout() { stdoutWorker.getOrCreate() }
    protected override fun startStderr() { stderrWorker.getOrCreate() }

    private fun Worker.Companion.execute(
        name: String,
        r: ReadStream,
        d: (line: String?) -> Unit,
    ): Worker {
        val w = start(name = "Process[pid=$pid, stdio=$name]")

        w.execute(TransferMode.SAFE, { Pair(r, d) }) { (reader, dispatch) ->
            reader.scanLines(dispatch)
        }

        return w
    }
}
