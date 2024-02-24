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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.time.Duration

@Suppress("NOTHING_TO_INLINE")
@Throws(InterruptedException::class)
internal expect inline fun Duration.threadSleep()

@Throws(IOException::class)
internal fun PlatformBuilder.blockingOutput(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    options: Output.Options,
    destroy: Signal,
): Output {

    val p = spawn(command, args, env, stdio, destroy)

    val stdoutBuffer = OutputFeedBuffer.of(options)
    val stderrBuffer = OutputFeedBuffer.of(options)

    var waitForCode: Int? = null

    try {
        p.stdoutFeed(stdoutBuffer)
        p.stderrFeed(stderrBuffer)

        waitForCode = p.commonWaitFor(options.timeout) {
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

    println("WAIT_FOR_CODE[$waitForCode]")
    val exitCode = waitForCode ?: try {
        // await for final closure if not ready yet
        p.waitFor()
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
        p.environment,
        p.stdio,
        p.destroySignal,
    )
}
