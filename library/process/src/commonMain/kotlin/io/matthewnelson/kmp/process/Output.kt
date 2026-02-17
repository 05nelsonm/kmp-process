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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "PropertyName", "UNUSED")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import kotlin.time.Duration

/**
 * TODO
 * */
public expect class Output {

    /**
     * TODO
     * */
    public val stdoutBuf: Buffered

    /**
     * TODO
     * */
    public val stderrBuf: Buffered

    /**
     * TODO
     * */
    public val processError: String?

    /**
     * TODO
     * */
    public val processInfo: ProcessInfo

    /**
     * TODO
     * */
    public abstract class Buffered internal constructor(length: Int) {

        /**
         * TODO
         * */
        public val length: Int

        /**
         * TODO
         * */
        public val indices: IntRange

        /**
         * TODO
         * */
        public abstract operator fun get(index: Int): Byte

        /**
         * TODO
         * */
        public abstract operator fun iterator(): ByteIterator

        /**
         * TODO
         * */
        public abstract fun utf8(): String
    }

    /**
     * TODO
     * */
    public sealed interface Feed

    /**
     * TODO
     * */
    public class Options {

        internal val maxBuffer: Int
        internal val timeout: Duration

        /**
         * TODO
         * */
        public class Builder private constructor() {

            internal var _inputBytes: (() -> ByteArray)?
                private set
            internal var _inputUtf8: (() -> String)?
                private set

            /**
             * TODO
             * */
            public fun input(block: () -> ByteArray): Builder

            /**
             * TODO
             * */
            public fun inputUtf8(block: () -> String): Builder

            /**
             * TODO
             * */
            public var maxBuffer: Int

            /**
             * TODO
             * */
            public var timeoutMillis: Int

            @PublishedApi
            internal companion object {

                @PublishedApi
                internal fun get(): Builder
            }

            internal fun build(): Options
        }

        internal val hasInput: Boolean

        @Throws(IOException::class)
        internal fun consumeInputBytes(): ByteArray?
        @Throws(IOException::class)
        internal fun consumeInputUtf8(): String?

        internal fun dropAllInput()
    }

    /**
     * TODO
     * */
    public class ProcessInfo {

        public val pid: Int
        public val exitCode: Int
        public val command: String
        public val args: List<String>
        public val cwd: File?
        public val environment: Map<String, String>
        public val stdio: Stdio.Config
        public val destroySignal: Signal

        internal companion object {
            internal fun createOutput(
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
            ): Output
        }

        /** @suppress */
        public override fun toString(): String
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
    public val stdout: String

    /**
     * DEPRECATED since `0.6.0`
     * @suppress
     * */
    @Deprecated(
        message = "Use stderrBuf.utf8",
        replaceWith = ReplaceWith("stderrBuf.utf8()"),
        level = DeprecationLevel.WARNING,
    )
    public val stderr: String

    /** @suppress */
    public override fun toString(): String
}
