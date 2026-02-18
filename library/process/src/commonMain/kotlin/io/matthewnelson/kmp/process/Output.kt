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
 * Results of [Process.Builder.createOutput] and [Process.Builder.createOutputAsync].
 * */
public expect class Output {

    /**
     * The buffered contents of [Process.stdout].
     * */
    public val stdoutBuf: Buffered

    /**
     * The buffered contents of [Process.stderr].
     * */
    public val stderrBuf: Buffered

    /**
     * If an error occurred with the [Process], such as the [Options.Builder.maxBuffer] or
     * [Options.Builder.timeoutMillis] being exceeded.
     * */
    public val processError: String?

    /**
     * Information about the [Process] that ran.
     * */
    public val processInfo: ProcessInfo

    /**
     * A read-only view of buffered I/O stream contents.
     * */
    public abstract class Buffered internal constructor(length: Int) {

        /**
         * The number of bytes buffered.
         * */
        public val length: Int

        public val indices: IntRange

        public abstract operator fun get(index: Int): Byte

        public abstract operator fun iterator(): ByteIterator

        /**
         * The UTF-8 decoded text of the buffered bytes.
         * */
        public abstract fun utf8(): String
    }

    /**
     * A "root" interface for obtaining [Process] I/O stream data.
     *
     * @see [OutputFeed]
     * @see [OutputFeed.Raw]
     * */
    public sealed interface Feed

    /**
     * Options for [Process.Builder.createOutput] and [Process.Builder.createOutputAsync]
     *
     * @see [Builder]
     * */
    public class Options {

        public class Builder private constructor() {

            internal var _inputBytes: (() -> ByteArray)?
                private set
            internal var _inputUtf8: (() -> String)?
                private set

            /**
             * DEFAULT: `null` (i.e. no input)
             *
             * Add any input that needs to be passed to the process's standard input stream, after it
             * has spawned.
             *
             * [block] is invoked once and only once. If it is not invoked due to an error, then the
             * reference to [block] is always dropped.
             *
             * [block] is always invoked lazily after the process has spawned, **except** when using the
             * blocking [Process.Builder.createOutput] call on Js/WasmJs which requires it as an argument
             * for `spawnSync`, so must be invoked beforehand.
             *
             * **NOTE:** After being written to stdin, the array produced by [block] is zeroed out before
             * its reference is dropped.
             *
             * **NOTE:** [block] will be called from the same thread that [Process.Builder.createOutput]
             * is called from, or within the same coroutine context that [Process.Builder.createOutputAsync]
             * is called from.
             *
             * Defining this input argument will override any [Process.Builder.stdin] configuration if it
             * is set to something other than [Stdio.Pipe].
             * */
            public fun input(block: () -> ByteArray): Builder

            /**
             * DEFAULT: `null` (i.e. no input)
             *
             * Add any input that needs to be passed to the process's standard input stream, after it
             * has spawned.
             *
             * [block] is invoked once and only once. If it is not invoked due to an error, then the
             * reference to [block] is always dropped.
             *
             * [block] is always invoked lazily after the process has spawned, **except** when using the
             * blocking [Process.Builder.createOutput] call on Js/WasmJs which requires it as an argument
             * for `spawnSync`, so must be invoked beforehand.
             *
             * **NOTE:** [block] will be called from the same thread that [Process.Builder.createOutput]
             * is called from, or within the same coroutine context that [Process.Builder.createOutputAsync]
             * is called from.
             *
             * Defining this input argument will override any [Process.Builder.stdin] configuration if it
             * is set to something other than [Stdio.Pipe].
             * */
            public fun inputUtf8(block: () -> String): Builder

            /**
             * DEFAULT: `1024 * 5000` on `Android`/`AndroidNative`/`iOS`, otherwise `Int.MAX_VALUE / 2`
             *
             * Define a maximum number of bytes that can be buffered on `stdout` or `stderr`. If exceeded,
             * the [Process] will be terminated and output truncated for the respective I/O streams, not
             * to exceed this setting.
             *
             * **NOTE:** If configured to less than `1024 * 16`, then `1024 * 16` will be used.
             * */
            public var maxBuffer: Int

            /**
             * DEFAULT: `250`
             *
             * Define a maximum number of milliseconds the [Process] is allowed to run for. If exceeded,
             * the [Process] will be terminated.
             *
             * **NOTE:** If configured to less than `250`, then `250` will be used.
             * */
            public var timeoutMillis: Int

            @PublishedApi
            internal companion object {

                @PublishedApi
                internal fun get(): Builder
            }

            internal fun build(): Options
        }

        internal val maxBuffer: Int
        internal val timeout: Duration

        internal val hasInput: Boolean

        @Throws(IOException::class)
        internal fun consumeInputBytes(): ByteArray?
        @Throws(IOException::class)
        internal fun consumeInputUtf8(): String?

        internal fun dropAllInput()
    }

    /**
     * Information about a [Process] which ran in order to produce an [Output].
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
