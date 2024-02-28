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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.process.internal.appendProcessInfo
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Output results from [Process.Builder.output]
 * */
public class Output private constructor(

    /**
     * The contents of [Process] `stdout` output
     * */
    @JvmField
    public val stdout: String,

    /**
     * The contents of [Process] `stderr` output
     * */
    @JvmField
    public val stderr: String,

    /**
     * If an error occurred with the [Process], such as
     * the [Options.maxBuffer] or [Options.timeout] was
     * exceeded.
     * */
    @JvmField
    public val processError: String?,

    /**
     * Information about the [Process] that ran.
     * */
    @JvmField
    public val processInfo: ProcessInfo,
) {

    /**
     * Options for [Process.Builder.output]
     *
     * @see [Builder]
     * */
    public class Options private constructor(
        internal val maxBuffer: Int,
        internal val timeout: Duration,
    ) {

        public class Builder private constructor() {

            // TODO: input

            /**
             * Maximum number of bytes that can be buffered
             * on `stdout` or `stderr`. If exceeded, [Process]
             * will be terminated and output truncated.
             *
             * Default: 2147483647 / 2
             * Minimum: 1024 * 16
             * */
            @JvmField
            public var maxBuffer: Int = Int.MAX_VALUE / 2

            /**
             * Maximum number of milliseconds the [Process] is
             * allowed to run. If exceeded, [Process] will be
             * terminated.
             *
             * Default: 250
             * Minimum: 250
             * */
            @JvmField
            public var timeoutMillis: Int = MIN_TIMEOUT

            internal companion object {

                private const val MIN_TIMEOUT: Int = 250
                private const val MIN_BUFFER: Int = 1024 * 16

                @JvmSynthetic
                internal fun build(
                    block: Builder.() -> Unit,
                ): Options {
                    val b = Builder().apply(block)

                    val maxBuffer = b.maxBuffer.let { max ->
                        if (max < MIN_BUFFER) MIN_BUFFER else max
                    }
                    val timeout = b.timeoutMillis.let { millis ->
                        (if (millis < MIN_TIMEOUT) MIN_TIMEOUT else millis)
                    }

                    return Options(maxBuffer, timeout.milliseconds)
                }
            }
        }
    }

    /**
     * Information about the [Process] that ran in order
     * to produce [Output].
     * */
    public class ProcessInfo private constructor(
        @JvmField
        public val pid: Int,
        @JvmField
        public val exitCode: Int,
        @JvmField
        public val command: String,
        @JvmField
        public val args: List<String>,
        @JvmField
        public val cwd: File?,
        @JvmField
        public val environment: Map<String, String>,
        @JvmField
        public val stdio: Stdio.Config,
        @JvmField
        public val destroySignal: Signal,
    ) {

        internal companion object {

            @JvmSynthetic
            internal fun createOutput(
                stdout: String,
                stderr: String,
                processError: String?,
                pid: Int,
                exitCode: Int,
                command: String,
                args: List<String>,
                cwd: File?,
                environment: Map<String, String>,
                stdio: Stdio.Config,
                destroySignal: Signal,
            ): Output = Output(
                stdout,
                stderr,
                processError,
                ProcessInfo(
                    pid,
                    exitCode,
                    command,
                    args,
                    cwd,
                    environment,
                    stdio,
                    destroySignal,
                )
            )
        }

        override fun toString(): String = buildString {
            appendProcessInfo(
                "Output.ProcessInfo",
                pid,
                exitCode.toString(),
                command,
                args,
                cwd,
                stdio,
                destroySignal
            )
        }
    }

    override fun toString(): String = buildString {
        appendLine("Output: [")
        appendLine("    stdout: [Omitted]")
        appendLine("    stderr: [Omitted]")
        append("    processError: ")
        appendLine(processError)

        processInfo.toString().lines().let { lines ->
            appendLine("    processInfo: [")
            for (i in 1 until lines.size) {
                append("    ")
                appendLine(lines[i])
            }
        }

        append(']')
    }
}
