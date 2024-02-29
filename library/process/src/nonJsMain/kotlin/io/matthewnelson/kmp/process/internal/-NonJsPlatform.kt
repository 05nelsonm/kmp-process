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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    val p = spawn(command, args, chdir, env, stdio, destroy)

    val stdoutBuffer = OutputFeedBuffer.of(options)
    val stderrBuffer = OutputFeedBuffer.of(options)

    var waitForCode: Int? = null

    try {
        p.stdoutFeed(stdoutBuffer)
        p.stderrFeed(stderrBuffer)

        val inputBytes = try {
            options.consumeInput()
        } catch (t: Throwable) {
            throw IOException("Output.Options.input invocation threw exception", t)
        }

        if (inputBytes != null) {
            p.input?.write(inputBytes)
                // Will never happen b/c Stdio.Config.Builder.build
                // will always set stdin to Stdio.Pipe when Output.Options.input
                // is not null, but must throw IOException instead of NPE using !!
                ?: throw IOException("Misconfigured Stdio.Config. stdin should be Stdio.Pipe")

            p.input.close()
        }

        try {
            // This is necessary to "guarantee" the stdout and
            // stderr threads start producing output before
            // potentially hopping out of waitFor and destroying
            // the process if it ended already.
            50.milliseconds.threadSleep()
        } catch (_: InterruptedException) {}

        // Output.Options.timeout is a minimum of 250 ms,
        // so will never be a negative value; we good.
        waitForCode = p.commonWaitFor(options.timeout - 50.milliseconds) {
            if (stdoutBuffer.maxSizeExceeded || stderrBuffer.maxSizeExceeded) {
                throw IllegalStateException()
            }

            it.threadSleep()
        }
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
