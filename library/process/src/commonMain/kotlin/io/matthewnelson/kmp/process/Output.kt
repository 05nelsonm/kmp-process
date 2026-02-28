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
    public val stdoutBuf: Data

    /**
     * The buffered contents of [Process.stderr].
     * */
    public val stderrBuf: Data

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
     *
     * @see [Output.stdoutBuf]
     * @see [Output.stderrBuf]
     * @see [OutputFeed.Raw]
     * */
    public abstract class Data internal constructor(size: Int, init: Any?): Collection<Byte> {

        /**
         * The number of bytes this instance contains.
         * */
        public final override val size: Int

        /**
         * Retrieves a byte at the given [index].
         *
         * @param [index] The index within [Data] to retrieve a byte from.
         *
         * @throws [IndexOutOfBoundsException] If [index] is inappropriate.
         * */
        public abstract operator fun get(index: Int): Byte

        public final override fun isEmpty(): Boolean
        public abstract override operator fun iterator(): ByteIterator
        public abstract override operator fun contains(element: Byte): Boolean
        public abstract override fun containsAll(elements: Collection<Byte>): Boolean

        /**
         * Copies the contents of this instance to a new array of bytes.
         * */
        public fun toByteArray(): ByteArray

        /**
         * Copies the contents of this instance to the provided [dest] array and returns that array.
         *
         * @param [dest] The array to copy to.
         * @param [destOffset] The index (inclusive) within [dest] to begin placing bytes.
         * @param [indexStart] The index (inclusive) within [Data] to begin retrieving bytes from.
         * @param [indexEnd] The index (exclusive) within [Data] to stop retrieving bytes at.
         *
         * @return The [dest] array.
         *
         * @throws [IndexOutOfBoundsException] or [IllegalArgumentException] when [indexStart] or [indexEnd]
         *   is out of range of this array indices or when `indexStart > indexEnd`.
         * @throws [IndexOutOfBoundsException] when the subrange doesn't fit into the [dest] array starting
         *   at the specified [destOffset], or when that index is out of the [dest] array indices range.
         * */
        public abstract fun copyInto(
            dest: ByteArray,
            destOffset: Int = 0,
            indexStart: Int = 0,
            indexEnd: Int = size,
        ): ByteArray

        /**
         * The UTF-8 decoded text of all bytes this instance contains.
         * */
        public abstract fun utf8(): String

        public companion object {

            /**
             * Consolidates multiple [Data] into a single instance, without duplicating any of the arrays
             * backing the [Data] instance(s).
             *
             * If the collection contains a single instance of [Data], then that instance is returned. If
             * the collection contains no data (i.e. the summed size is `0`), then the empty [Data] object
             * instance is returned. Any `null` or empty [Data] within the collection are dropped.
             *
             * e.g.
             *
             *     println(data1.utf8())
             *     println(data2.utf8())
             *     println(listOf(data1, data2).consolidate().utf8())
             *     println(listOf(data2, data1).consolidate().utf8())
             *     println(listOf(data1, data1).consolidate().utf8())
             *
             *     // Hello World --ONE--!
             *     // Hello World --TWO--!
             *     // Hello World --ONE--!Hello World --TWO--!
             *     // Hello World --TWO--!Hello World --ONE--!
             *     // Hello World --ONE--!Hello World --ONE--!
             *
             * @throws [RuntimeException] If total [size] of consolidated [Data] would exceed [Int.MAX_VALUE].
             * */
            public fun Collection<Data?>.consolidate(): Data
        }

        /** @suppress */
        public final override fun toString(): String
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
