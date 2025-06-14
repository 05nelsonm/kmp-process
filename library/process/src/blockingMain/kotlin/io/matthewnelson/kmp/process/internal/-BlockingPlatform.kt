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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Throws(InterruptedException::class)
internal expect inline fun Duration.threadSleep()

internal expect inline fun Process.wasStdoutThreadStarted(): Boolean
internal expect inline fun Process.wasStderrThreadStarted(): Boolean

@Throws(IOException::class)
internal fun PlatformBuilder.blockingOutput(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    options: Output.Options,
    destroy: Signal,
): Output {

    val p = try {
        spawn(command, args, chdir, env, stdio, destroy, ProcessException.Handler.IGNORE)
    } catch (e: IOException) {
        options.dropInput()
        throw e
    }

    val stdoutBuffer = OutputFeedBuffer.of(options)
    val stderrBuffer = OutputFeedBuffer.of(options)

    var waitForCode: Int? = null

    try {
        p.stdoutFeed(stdoutBuffer)
        p.stderrFeed(stderrBuffer)

        options.consumeInput()?.let { bytes ->
            try {
                p.input?.write(bytes)
                    // Will never happen b/c Stdio.Config.Builder.build
                    // will always set stdin to Stdio.Pipe when Output.Options.input
                    // is not null, but must throw IOException instead of NPE using !!
                    ?: throw IOException("Misconfigured Stdio.Config. stdin should be Stdio.Pipe")

                p.input.close()
            } finally {
                bytes.fill(0)
            }
        }

        condition {
            if (p.wasStdoutThreadStarted() && p.wasStderrThreadStarted()) {
                // One final sleep before handing it off, just to
                // ensure data is flowing through the readers.
                5.milliseconds.threadSleep()
            } else {
                null
            }
        }.commonWaitFor(
            timeout = ((options.timeout - 25.milliseconds) - p.startTime.elapsedNow()).coerceAtLeast(1.milliseconds),
            sleep = { millis -> millis.threadSleep() },
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
            timeout = (options.timeout - p.startTime.elapsedNow()).coerceAtLeast(1.milliseconds),
            sleep = { millis ->
                if (stdoutBuffer.maxSizeExceeded || stderrBuffer.maxSizeExceeded) throw IllegalStateException()
                millis.threadSleep()
            },
        )
    } catch (_: IllegalStateException) {
        // max buffer exceeded and it hopped out of waitFor
    } catch (e: InterruptedException) {
        throw IOException("Underlying thread interrupted", e)
    } finally {
        p.destroy()
    }

    val exitCode = try {
        p.stdoutWaiter()
            .awaitStop()
            .stderrWaiter()
            .awaitStop()
            .waitFor()
    } catch (e: InterruptedException) {
        throw IOException("Underlying thread interrupted", e)
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

internal fun ReadStream.scanLines(
    dispatch: (line: String?) -> Unit,
) { scanLines(1024 * 8, dispatch) }

@OptIn(InternalProcessApi::class)
@Throws(IllegalArgumentException::class)
internal fun ReadStream.scanLines(
    bufferSize: Int,
    dispatch: (line: String?) -> Unit,
) {

    val stream = this
    val buf = ReadBuffer.of(ByteArray(bufferSize))
    val feed = ReadBuffer.lineOutputFeed(dispatch)

    var threw: Throwable? = null
    while (true) {
        val read = try {
            stream.read(buf.buf)
        } catch (_: IOException) {
            break
        }

        // If a pipe has no write ends open (i.e. the
        // child process exited), a zero read is returned,
        // and we can end early (before process destruction).
        if (read <= 0) break

        try {
            feed.onData(buf, read)
        } catch (t: Throwable) {
            threw = t
            break
        }
    }

    buf.buf.fill(0)
    threw?.let { throw it }
    feed.close()
}
