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
import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.OUTPUT_OPTIONS_MIN_TIMEOUT
import io.matthewnelson.kmp.process.internal.commonBuild
import io.matthewnelson.kmp.process.internal.commonToByteArray
import io.matthewnelson.kmp.process.internal.commonConsumeInput
import io.matthewnelson.kmp.process.internal.commonHasInput
import io.matthewnelson.kmp.process.internal.commonInit
import io.matthewnelson.kmp.process.internal.commonIsEmpty
import io.matthewnelson.kmp.process.internal.commonToString
import io.matthewnelson.kmp.process.internal.commonMaxBufferDefault
import io.matthewnelson.kmp.process.internal.commonMerge
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import kotlin.time.Duration

// jvmMain
public actual class Output private constructor(
    @JvmField
    public actual val stdoutBuf: Data,
    @JvmField
    public actual val stderrBuf: Data,
    @JvmField
    public actual val processError: String?,
    @JvmField
    public actual val processInfo: ProcessInfo,
) {

    public actual abstract class Data internal actual constructor(
        public actual final override val size: Int,
        private val segments: Array<Bit8Array>,
        private val sizes: IntArray?,
        init: Any,
    ): Collection<Byte> {

        @Volatile
        private var _bb: ByteBuffer? = null

        public actual abstract operator fun get(index: Int): Byte

        public actual final override fun isEmpty(): Boolean = commonIsEmpty()
        public actual abstract override operator fun iterator(): ByteIterator
        public actual abstract override fun contains(element: Byte): Boolean
        public actual abstract override fun containsAll(elements: Collection<Byte>): Boolean

        public actual fun toByteArray(): ByteArray = commonToByteArray()
        public actual abstract fun copyInto(
            dest: ByteArray,
            destOffset: Int/* = 0*/,
            indexStart: Int/* = 0*/,
            indexEnd: Int/* = size*/,
        ): ByteArray
        public actual abstract fun utf8(): String

        /**
         * The contents of this instances as a read-only [ByteBuffer].
         * */
        public fun asByteBuffer(): ByteBuffer {
            val bb = _bb ?: when {
                segments.size == 1 -> segments[0].storage
                else -> toByteArray()
            }.let { array -> ByteBuffer.wrap(array).also { _bb = it } }
            return bb.asReadOnlyBuffer()
        }

        // TODO: copyInto for dest ByteBuffer

        public actual companion object {
            @JvmStatic
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
    @JvmField // << Unfortunately cannot convert to a getter
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
    @JvmField // << Unfortunately cannot convert to a getter
    public actual val stderr: String = stderrBuf.utf8()

    /** @suppress */
    public actual override fun toString(): String = commonToString()
}
