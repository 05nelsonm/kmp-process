/*
 * Copyright (c) 2026 Matthew Nelson
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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "PropertyName")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.OUTPUT_OPTIONS_MIN_TIMEOUT
import io.matthewnelson.kmp.process.internal.commonBuild
import io.matthewnelson.kmp.process.internal.commonConsumeInput
import io.matthewnelson.kmp.process.internal.commonHasInput
import io.matthewnelson.kmp.process.internal.commonToString
import io.matthewnelson.kmp.process.internal.commonMaxBufferDefault
import kotlin.concurrent.Volatile
import kotlin.time.Duration

// jvmMain
public actual class Output private constructor(
    @JvmField
    public actual val stdoutBuf: Buffered,
    @JvmField
    public actual val stderrBuf: Buffered,
    @JvmField
    public actual val processError: String?,
    @JvmField
    public actual val processInfo: ProcessInfo,
) {

    public actual abstract class Buffered internal actual constructor(@JvmField public actual val length: Int) {
        public actual val indices: IntRange get() = IntRange(0, length - 1)
        public actual abstract operator fun get(index: Int): Byte
        public actual abstract operator fun iterator(): ByteIterator
        public actual abstract fun utf8(): String
    }

    public actual sealed interface Feed

    public actual class Options private constructor(
        @Volatile
        private var inputBytes: (() -> ByteArray)?,
        @Volatile
        private var inputUtf8: (() -> String)?,
        @get:JvmSynthetic
        internal actual val maxBuffer: Int,
        @get:JvmSynthetic
        internal actual val timeout: Duration,
    ) {

        public actual class Builder private actual constructor() {

            @get:JvmSynthetic
            internal actual var _inputBytes: (() -> ByteArray)? = null
                private set
            @get:JvmSynthetic
            internal actual var _inputUtf8: (() -> String)? = null
                private set

            public actual fun input(block: () -> ByteArray): Builder = apply {
                _inputBytes = block
                _inputUtf8 = null
            }

            public actual fun inputUtf8(block: () -> String): Builder = apply {
                _inputBytes = null
                _inputUtf8 = block
            }

            @JvmField
            public actual var maxBuffer: Int = commonMaxBufferDefault()
            @JvmField
            public actual var timeoutMillis: Int = OUTPUT_OPTIONS_MIN_TIMEOUT

            @PublishedApi
            internal actual companion object {

                @PublishedApi
                @JvmSynthetic
                internal actual fun get(): Builder = Builder()
            }

            @JvmSynthetic
            internal actual fun build(): Options = ::Options.commonBuild(this)
        }

        @get:JvmSynthetic
        internal actual val hasInput: Boolean get() = commonHasInput(inputBytes, inputUtf8)

        @JvmSynthetic
        @Throws(IOException::class)
        internal actual fun consumeInputBytes(): ByteArray? = commonConsumeInput(inputBytes) { inputBytes = null }

        @JvmSynthetic
        @Throws(IOException::class)
        internal actual fun consumeInputUtf8(): String? = commonConsumeInput(inputUtf8) { inputUtf8 = null }

        @JvmSynthetic
        internal actual fun dropAllInput() { inputBytes = null; inputUtf8 = null }
    }

    public actual class ProcessInfo private constructor(
        @JvmField
        public actual val pid: Int,
        @JvmField
        public actual val exitCode: Int,
        @JvmField
        public actual val command: String,
        @JvmField
        public actual val args: List<String>,
        @JvmField
        public actual val cwd: File?,
        @JvmField
        public actual val environment: Map<String, String>,
        @JvmField
        public actual val stdio: Stdio.Config,
        @JvmField
        public actual val destroySignal: Signal,
    ) {

        internal actual companion object {

            @JvmSynthetic
            internal actual fun createOutput(
                stdoutBuf: Buffered,
                stderrBuf: Buffered,
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
                stdoutBuf,
                stderrBuf,
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
                ),
            )
        }

        /** @suppress */
        public actual override fun toString(): String = commonToString()
    }

    /**
     * DEPRECATED since `0.6.0`
     * @suppress
     * */
    @Deprecated(
        message = "Use stdoutBuf.utf8",
        replaceWith = ReplaceWith("stdoutBuf.utf8()"),
        level = DeprecationLevel.WARNING,
    )
    @JvmField
    public actual val stdout: String = stdoutBuf.utf8()

    /**
     * DEPRECATED since `0.6.0`
     * @suppress
     * */
    @Deprecated(
        message = "Use stderrBuf.utf8",
        replaceWith = ReplaceWith("stderrBuf.utf8()"),
        level = DeprecationLevel.WARNING,
    )
    @JvmField
    public actual val stderr: String = stderrBuf.utf8()

    /** @suppress */
    public actual override fun toString(): String = commonToString()
}
