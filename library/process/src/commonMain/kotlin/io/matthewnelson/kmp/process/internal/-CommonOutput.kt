/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.AsyncWriteStream
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val CONTEXT_MAX_BUFFER_SIZE = "Max buffer size was exceeded"

internal expect inline fun Process.hasStdoutStarted(): Boolean
internal expect inline fun Process.hasStderrStarted(): Boolean

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun PlatformBuilder.commonOutput(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    options: Output.Options,
    destroy: Signal,
    _spawn: PlatformBuilder.(String, List<String>, File?, Map<String, String>, Stdio.Config, Signal, ProcessException.Handler) -> Process,
    _close: AsyncWriteStream.() -> Unit,
    _write: AsyncWriteStream.(ByteArray, Int, Int) -> Unit,
    _sleep: (Duration) -> Unit,
    // Specifically for coroutines
    _sleepWithContext: (Duration) -> Unit,
    _awaitStop: OutputFeed.Waiter.() -> Process,
    _waitFor: Process.() -> Int,
): Output {
    contract {
        callsInPlace(_spawn, InvocationKind.EXACTLY_ONCE)
        callsInPlace(_close, InvocationKind.AT_MOST_ONCE)
        callsInPlace(_write, InvocationKind.UNKNOWN)
        callsInPlace(_sleep, InvocationKind.UNKNOWN)
        callsInPlace(_sleepWithContext, InvocationKind.UNKNOWN)
        callsInPlace(_awaitStop, InvocationKind.UNKNOWN)
        callsInPlace(_waitFor, InvocationKind.AT_MOST_ONCE)
    }

    val p = try {
        _spawn(command, args, chdir, env, stdio, destroy, ProcessException.Handler.IGNORE)
    } catch (t: Throwable) {
        options.dropAllInput()
        // CancellationException or IOException
        throw t
    }

    // Cannot use Process.startTime because implementation may block for a moment
    // until indication that the child process has actually spawned. So, use the
    // time at when things were returned to us.
    val startTime = TimeSource.Monotonic.markNow()

    val stdoutBuffer = OutputFeedBuffer.of(options)
    val stderrBuffer = OutputFeedBuffer.of(options)

    var waitForCode: Int? = null
    try {
        p.stdoutFeed(stdoutBuffer)
        p.stderrFeed(stderrBuffer)

        // input will be non-null if and only if Output.Options.hasInput is true.
        p.input?.writeInputAndClose(options, _close, _write)

        // Ensure Jvm/Native threads are in their read loops
        condition {
            if (p.hasStdoutStarted() && p.hasStderrStarted()) Unit else null
        }.commonWaitFor(
            timeout = ((options.timeout - 25.milliseconds) - startTime.elapsedNow()).coerceAtLeast(1.milliseconds),
            sleep = _sleep,
        )

        var postExitTicks = 0

        condition {
            val code = p.exitCodeOrNull()
            waitForCode = code

            // Only allow a maximum of 5 ticks after the process has exited
            // before returning a non-null condition, regardless of whether
            // the buffers have seen a last line. Each sleep is for a duration
            // of 100ms, totaling in an additional 500ms after process exit
            // unless we've reached the timeout.
            //
            // If the underlying pipe was created in a non-atomic manner with
            // pipe(1), then there is a potential for the write end to have
            // been leaked to another process. Our read end that is blocking,
            // waiting for more data, will not "pop out" from the read unless
            // we close the descriptor. So, popping out here will destroy the
            // process and close it for us.
            //
            // If the configured timeout is something wildly inappropriate like
            // Int.MAX_VALUE, this matters because we'd never time out.
            if (code != null && ++postExitTicks >= 5) return@condition code

            if (stdoutBuffer.hasEnded && stderrBuffer.hasEnded) return@condition code

            null
        }.commonWaitFor(
            timeout = (options.timeout - startTime.elapsedNow()).coerceAtLeast(1.milliseconds),
            sleep = { millis ->
                if (stdoutBuffer.maxSizeExceeded || stderrBuffer.maxSizeExceeded) {
                    throw ProcessException.of(CONTEXT_MAX_BUFFER_SIZE, Throwable())
                }
                if (postExitTicks > 0) _sleepWithContext(millis) else _sleep(millis)
            },
        )
    } catch (e: ProcessException) {
        if (e.context != CONTEXT_MAX_BUFFER_SIZE) throw e.wrapIOException()
        // Max buffer size has been exceeded and sleep popped out.
    } catch (e: InterruptedException) {
        throw e.wrapIOException { "Underlying thread interrupted" }
    } finally {
        // OK b/c using Handler.IGNORE
        p.destroy()
    }

    val exitCode = try {
        p.stdoutWaiter()
            ._awaitStop()
            .stderrWaiter()
            ._awaitStop()
            ._waitFor()
    } catch (e: InterruptedException) {
        throw e.wrapIOException { "Underlying thread interrupted" }
    }

    val pErr = when {
        stdoutBuffer.maxSizeExceeded || stderrBuffer.maxSizeExceeded -> {
            "maxBuffer[${options.maxBuffer}] exceeded"
        }
        waitForCode == null -> {
            "waitFor timed out"
        }
        else -> null
    }

    val stdout = stdoutBuffer.doFinal()
    val stderr = stderrBuffer.doFinal()

    return Output.ProcessInfo.createOutput(
        stdout,
        stderr,
        pErr,
        p.pid(),
        exitCode,
        p.command,
        p.args,
        p.cwd,
        p.environment,
        p.stdio,
        p.destroySignal,
    )
}

@Suppress("WRONG_INVOCATION_KIND")
@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun AsyncWriteStream.writeInputAndClose(
    options: Output.Options,
    _close: AsyncWriteStream.() -> Unit,
    _write: AsyncWriteStream.(ByteArray, Int, Int) -> Unit,
) {
    contract {
        callsInPlace(_close, InvocationKind.EXACTLY_ONCE)
        callsInPlace(_write, InvocationKind.UNKNOWN)
    }

    var threw: Throwable? = null
    try {
        options.consumeInputBytes()?.let { b ->
            try {
                _write(b, 0, b.size)
            } finally {
                b.fill(0)
            }
        }
        options.consumeInputUtf8()?.let { text ->
            if (text.length <= (8192 / 3)) {
                val utf8 = text.decodeToByteArray(UTF8)
                try {
                    _write(utf8, 0, utf8.size)
                    return@let
                } finally {
                    utf8.fill(0)
                }
            }

            // Chunk
            val buf = ByteArray(8192)
            val limit = buf.size - 4
            var iBuf = 0
            var iText = 0
            try {
                UTF8.newDecoderFeed { b -> buf[iBuf++] = b }.use { feed ->
                    while (iText < text.length) {
                        feed.consume(text[iText++])
                        if (iBuf <= limit) continue
                        _write(buf, 0, iBuf)
                        iBuf = 0
                    }
                }
                if (iBuf > 0) _write(buf, 0, iBuf)
            } finally {
                buf.fill(0)
            }
        }
    } catch (t: Throwable) {
        threw = t
        throw t
    } finally {
        try {
            _close()
        } catch (tt: Throwable) {
            threw?.addSuppressed(tt) ?: run { throw tt }
        }
    }
}
