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
import io.matthewnelson.kmp.process.internal.commonBytes
import io.matthewnelson.kmp.process.internal.commonConsumeInput
import io.matthewnelson.kmp.process.internal.commonHasInput
import io.matthewnelson.kmp.process.internal.commonInit
import io.matthewnelson.kmp.process.internal.commonIsEmpty
import io.matthewnelson.kmp.process.internal.commonToString
import io.matthewnelson.kmp.process.internal.commonMaxBufferDefault
import io.matthewnelson.kmp.process.internal.commonMerge
import kotlin.concurrent.Volatile
import kotlin.time.Duration

// nativeMain
public actual class Output private constructor(
    public actual val stdoutBuf: Data,
    public actual val stderrBuf: Data,
    public actual val processError: String?,
    public actual val processInfo: ProcessInfo,
) {

    public actual abstract class Data internal actual constructor(
        public actual final override val size: Int,
        private val segments: Array<ReadBuffer>,
        private val sizes: IntArray?,
        init: Any,
    ): Collection<Byte> {

        public actual abstract operator fun get(index: Int): Byte

        public actual final override fun isEmpty(): Boolean = commonIsEmpty()
        public actual abstract override operator fun iterator(): ByteIterator
        public actual abstract override fun contains(element: Byte): Boolean
        public actual abstract override fun containsAll(elements: Collection<Byte>): Boolean

        public actual fun bytes(): ByteArray = commonBytes()
        public actual abstract fun copyInto(
            dest: ByteArray,
            destOffset: Int/* = 0*/,
            indexStart: Int/* = 0*/,
            indexEnd: Int/* = size*/,
        ): ByteArray
        public actual abstract fun utf8(): String

        public actual companion object {
            public actual fun Collection<Data?>.merge(): Data = commonMerge(_segmentsGet = Data::segments)
        }

        /** @suppress */
        public actual final override fun toString(): String = commonToString()

        init { commonInit(init) }
    }

    public actual sealed interface Feed

    public actual class Options private constructor(
        @Volatile
        private var inputBytes: (() -> ByteArray)?,
        @Volatile
        private var inputUtf8: (() -> String)?,
        internal actual val maxBuffer: Int,
        internal actual val timeout: Duration,
    ) {

        public actual class Builder private actual constructor() {

            internal actual var _inputBytes: (() -> ByteArray)? = null
                private set
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

            public actual var maxBuffer: Int = commonMaxBufferDefault()
            public actual var timeoutMillis: Int = OUTPUT_OPTIONS_MIN_TIMEOUT

            @PublishedApi
            internal actual companion object {

                @PublishedApi
                internal actual fun get(): Builder = Builder()
            }

            internal actual fun build(): Options = ::Options.commonBuild(this)
        }

        internal actual val hasInput: Boolean get() = commonHasInput(inputBytes, inputUtf8)

        @Throws(IOException::class)
        internal actual fun consumeInputBytes(): ByteArray? = commonConsumeInput(inputBytes) { inputBytes = null }

        @Throws(IOException::class)
        internal actual fun consumeInputUtf8(): String? = commonConsumeInput(inputUtf8) { inputUtf8 = null }

        internal actual fun dropAllInput() { inputBytes = null; inputUtf8 = null }
    }

    public actual class ProcessInfo private constructor(
        public actual val pid: Int,
        public actual val exitCode: Int,
        public actual val command: String,
        public actual val args: List<String>,
        public actual val cwd: File?,
        public actual val environment: Map<String, String>,
        public actual val stdio: Stdio.Config,
        public actual val destroySignal: Signal,
    ) {

        internal actual companion object {

            internal actual fun createOutput(
                stdoutBuf: Data,
                stderrBuf: Data,
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
    public actual val stdout: String get() = stdoutBuf.utf8()

    /**
     * DEPRECATED since `0.6.0`
     * @suppress
     * */
    @Deprecated(
        message = "Use stderrBuf.utf8",
        replaceWith = ReplaceWith("stderrBuf.utf8()"),
        level = DeprecationLevel.WARNING,
    )
    public actual val stderr: String get() = stderrBuf.utf8()

    /** @suppress */
    public actual override fun toString(): String = commonToString()
}
