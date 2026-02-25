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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE", "RedundantVisibilityModifier", "RemoveRedundantQualifierName")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDERR
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDOUT
import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.Lock
import io.matthewnelson.kmp.process.internal.OutputFeedBuffer
import io.matthewnelson.kmp.process.internal.Utf8LineDispatcher
import io.matthewnelson.kmp.process.internal.asOutputData
import io.matthewnelson.kmp.process.internal.empty
import io.matthewnelson.kmp.process.internal.newLock
import io.matthewnelson.kmp.process.internal.withLock
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds

/**
 * A callback for obtaining `stdout` and `stderr` output as UTF-8 lines.
 *
 * **NOTE:** `Jvm` and `Native` [onOutput] is called from a background thread; either the `stdout`
 * thread or the `stderr` thread. It is ill-advised to register the same [Output.Feed] to both
 * [Handler.stdout] and [Handler.stderr].
 *
 * **NOTE:** Any exceptions that [onOutput] throws will be delegated to [ProcessException.Handler] (if
 * configured). If it is re-thrown by the [ProcessException.Handler], the [Process] will be terminated,
 * and all [Output.Feed] for that I/O stream will be ejected immediately.
 *
 * e.g.
 *
 *     val exitCode = builder.createProcessAsync().use { p ->
 *         p.stdout(
 *             OutputFeed { line -> println(line ?: "--STDOUT EOS--") },
 *             OutputFeed.Raw { data ->
 *                 println(data?.utf8() ?: "--STDOUT EOS--")
 *             },
 *         ).stderr(
 *             OutputFeed { line -> println(line ?: "--STDERR EOS--") },
 *         ).waitForAsync(500.milliseconds)
 *         p // << Return Process to use
 *     } // << Process.destroy() called on use lambda closure
 *         .stdoutWaiter()
 *         .awaitStopAsync()
 *         .stderrWaiter()
 *         .awaitStopAsync()
 *         .waitForAsync()
 *
 * @see [Raw]
 * @see [Handler.stdout]
 * @see [Handler.stderr]
 * */
public fun interface OutputFeed: Output.Feed {

    /**
     * Receive a single UTF-8 line of output from [Process], or `null` to indicate end-of-stream.
     *
     * @param [line] The single UTF-8 line of output, or `null` to indicate end-of-stream.
     * */
    public fun onOutput(line: String?)

    /**
     * A callback for obtaining `stdout` and `stderr` output bytes.
     *
     * **NOTE:** `Jvm` and `Native` [onOutput] is called from a background thread; either the `stdout`
     * thread or the `stderr` thread. It is ill-advised to register the same [Output.Feed] to both
     * [Handler.stdout] and [Handler.stderr].
     *
     * **NOTE:** Any exceptions that [onOutput] throws will be delegated to [ProcessException.Handler] (if
     * configured). If it is re-thrown by the [ProcessException.Handler], the [Process] will be terminated,
     * and all [Output.Feed] for that I/O stream will be ejected immediately.
     *
     * e.g.
     *
     *     val exitCode = builder.createProcessAsync().use { p ->
     *         p.stdout(
     *             OutputFeed { line -> println(line ?: "--STDOUT EOS--") },
     *             OutputFeed.Raw { data ->
     *                 println(data?.utf8() ?: "--STDOUT EOS--")
     *             },
     *         ).stderr(
     *             OutputFeed { line -> println(line ?: "--STDERR EOS--") },
     *         ).waitForAsync(500.milliseconds)
     *         p // << Return Process to use
     *     } // << Process.destroy() called on use lambda closure
     *         .stdoutWaiter()
     *         .awaitStopAsync()
     *         .stderrWaiter()
     *         .awaitStopAsync()
     *         .waitForAsync()
     *
     * @see [OutputFeed]
     * @see [Handler.stdout]
     * @see [Handler.stderr]
     * */
    public fun interface Raw: Output.Feed {

        /**
         * Receive a read-only view to the buffered bytes of output from [Process].
         *
         * @param [data] The [Output.Data], or `null` to indicate end-of-stream.
         * */
        public fun onOutput(data: Output.Data?)
    }

    /**
     * Helper class which [Process] extends that handles everything regarding dispatching of `stdout` and
     * `stderr` I/O stream output to attached [Output.Feed].
     *
     * **NOTE:** Reading of [Process] output begins upon first attachment of [Output.Feed] for the respective
     * [stdout] or [stderr] I/O stream. When the respective [stdout] or [stderr] I/O stream ends, all attached
     * [Output.Feed] for the respective I/O stream are dereferenced.
     * */
    public sealed class Handler(stdio: Stdio.Config): Blocking() {

        /**
         * Is set to `true` via [Process.destroyProtected] implementation.
         * @suppress
         * */
        @JvmField
        @Volatile
        protected var isDestroyed: Boolean = false

        @Volatile
        private var _stdoutStarted = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var _stdoutStopped = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var _stderrStarted = stdio.stderr !is Stdio.Pipe
        @Volatile
        private var _stderrStopped = stdio.stderr !is Stdio.Pipe
        @Volatile
        private var _stdoutFeeds = emptyArray<Output.Feed>()
        @Volatile
        private var _stderrFeeds = emptyArray<Output.Feed>()

        private val stdoutLock = if (stdio.stdout !is Stdio.Pipe) null else newLock()
        private val stderrLock = if (stdio.stderr !is Stdio.Pipe) null else newLock()

        /**
         * Attach a single [Output.Feed] to obtain `stdout` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stdout] is **not** [Stdio.Pipe]
         *  - The `stdout` I/O stream has ended
         *  - [Process.destroy] has been called
         *
         * @param [feed] The [OutputFeed] or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stdout(feed: Output.Feed): Process {
            return addStdoutFeeds(mutableSetOf(feed))
        }

        /**
         * Attach multiple [Output.Feed] to obtain `stdout` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stdout] is **not** [Stdio.Pipe]
         *  - The `stdout` I/O stream has ended
         *  - [Process.destroy] has been called
         *  - [feeds] is empty
         *
         * @param [feeds] The [OutputFeed] and/or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stdout(vararg feeds: Output.Feed): Process = stdoutVararg(feeds)

        /**
         * Attach multiple [Output.Feed] to obtain `stdout` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stdout] is **not** [Stdio.Pipe]
         *  - The `stdout` I/O stream has ended
         *  - [Process.destroy] has been called
         *  - [feeds] is empty
         *
         * @param [feeds] The [OutputFeed] and/or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stdout(feeds: Collection<Output.Feed>): Process {
            if (feeds.isEmpty()) return _this
            return addStdoutFeeds(feeds.toMutableSet())
        }

        /**
         * Attach a single [Output.Feed] to obtain `stderr` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stderr] is **not** [Stdio.Pipe]
         *  - The `stderr` I/O stream has ended
         *  - [Process.destroy] has been called
         *
         * @param [feed] The [OutputFeed] or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stderr(feed: Output.Feed): Process {
            return addStderrFeeds(mutableSetOf(feed))
        }

        /**
         * Attach multiple [Output.Feed] to obtain `stderr` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stderr] is **not** [Stdio.Pipe]
         *  - The `stderr` I/O stream has ended
         *  - [Process.destroy] has been called
         *  - [feeds] is empty
         *
         * @param [feeds] The [OutputFeed] and/or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stderr(vararg feeds: Output.Feed): Process = stderrVararg(feeds)

        /**
         * Attach multiple [Output.Feed] to obtain `stderr` output.
         *
         * **NOTE:** This function has no effect when:
         *  - [Stdio.Config.stderr] is **not** [Stdio.Pipe]
         *  - The `stderr` I/O stream has ended
         *  - [Process.destroy] has been called
         *  - [feeds] is empty
         *
         * @param [feeds] The [OutputFeed] and/or [OutputFeed.Raw] to attach.
         *
         * @return The [Process] for chaining operations.
         * */
        public fun stderr(feeds: Collection<Output.Feed>): Process {
            if (feeds.isEmpty()) return _this
            return addStderrFeeds(feeds.toMutableSet())
        }

        /**
         * Produces a new [OutputFeed.Waiter] for `stdout` in order to await any final output after resource
         * closure occurs.
         *
         * @return The [OutputFeed.Waiter]
         *
         * @throws [IllegalStateException] If [Process.destroy] has not been called yet.
         * */
        public fun stdoutWaiter(): OutputFeed.Waiter = object : RealWaiter(_this, isDestroyed) {
            override fun isStarted(): Boolean = _stdoutStarted
            override fun isStopped(): Boolean = _stdoutStopped
        }

        /**
         * Produces a new [OutputFeed.Waiter] for `stderr` in order to await any final output after resource
         * closure occurs.
         *
         * @return The [OutputFeed.Waiter]
         *
         * @throws [IllegalStateException] If [Process.destroy] has not been called yet.
         * */
        public fun stderrWaiter(): OutputFeed.Waiter = object : RealWaiter(_this, isDestroyed) {
            override fun isStarted(): Boolean = _stderrStarted
            override fun isStopped(): Boolean = _stderrStopped
        }

        /** @suppress */
        @Throws(Throwable::class)
        protected abstract fun onError(t: Throwable, context: String)
        /** @suppress */
        protected abstract fun startStdout()
        /** @suppress */
        protected abstract fun startStderr()

        // protected functions with internal type parameters is not a thing, so to maintain
        // encapsulation without opening things up as internal, Process implementations will
        // obtain the private function reference this way when starting stdout/stderr. This
        // is especially necessary for Native because it uses Worker, requiring the reference
        // to be passed in at time of execution.
        /** @suppress */
        protected companion object {

            @JvmSynthetic
            internal fun Handler.dispatchStdoutRef(): (Bit8Array?, Int) -> Unit = ::dispatchStdoutData

            @JvmSynthetic
            internal fun Handler.dispatchStderrRef(): (Bit8Array?, Int) -> Unit = ::dispatchStderrData
        }

        private fun dispatchStdoutData(buf: Bit8Array?, len: Int) {
            var data: Output.Data? = null
            dispatch(
                thing = buf,
                onErrorContext = CTX_FEED_STDOUT,
                feedsLock = stdoutLock,
                _onFeed = { onFeedRaw(it, len, _dataGet = { data }, _dataSet = { new -> data = new }) },
                _feedsGet = { _stdoutFeeds },
                _feedsSet = { new -> _stdoutFeeds = new },
                _stoppedSet = { new -> _stdoutStopped = new },
            )
        }

        private fun dispatchStderrData(buf: Bit8Array?, len: Int) {
            var data: Output.Data? = null
            dispatch(
                thing = buf,
                onErrorContext = CTX_FEED_STDERR,
                feedsLock = stderrLock,
                _onFeed = { onFeedRaw(it, len, _dataGet = { data }, _dataSet = { new -> data = new }) },
                _feedsGet = { _stderrFeeds },
                _feedsSet = { new -> _stderrFeeds = new },
                _stoppedSet = { new -> _stderrStopped = new },
            )
        }

        @Throws(Throwable::class)
        private fun dispatchStdoutLine(line: String?) {
            dispatch(
                thing = line,
                onErrorContext = CTX_FEED_STDOUT,
                // Do not do closure here. This is being called from the Utf8LineDispatcher,
                // which is being called from dispatchStdoutData where actual closure will
                // be had.
                feedsLock = null,
                _onFeed = { (this as? OutputFeed)?.onOutput(it) },
                _feedsGet = { _stdoutFeeds },
                _feedsSet = {},
                _stoppedSet = {},
            )
        }

        @Throws(Throwable::class)
        private fun dispatchStderrLine(line: String?) {
            dispatch(
                thing = line,
                onErrorContext = CTX_FEED_STDERR,
                // Do not do closure here. This is being called from the Utf8LineDispatcher,
                // which is being called from dispatchStdoutData where actual closure will
                // be had.
                feedsLock = null,
                _onFeed = { (this as? OutputFeed)?.onOutput(it) },
                _feedsGet = { _stderrFeeds },
                _feedsSet = {},
                _stoppedSet = {},
            )
        }

        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        @OptIn(ExperimentalContracts::class)
        private inline fun Output.Feed.onFeedRaw(
            buf: Bit8Array?,
            len: Int,
            _dataGet: () -> Output.Data?,
            _dataSet: (new: Output.Data) -> Unit,
        ) {
            contract {
                callsInPlace(_dataGet, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_dataSet, InvocationKind.AT_MOST_ONCE)
            }

            when (this) {
                is OutputFeed -> {}

                // Will only be the case if OutputFeed are present in feeds.
                is Utf8LineDispatcher -> if (buf != null) {
                    update(offset = 0, len = len, get = buf::get)
                } else {
                    doFinal()
                }

                // Will only be the case if this Process is for creating Output
                // via Process.Builder.{createOutput/createOutputAsync}.
                is OutputFeedBuffer -> update(buf, len)

                is OutputFeed.Raw -> if (buf == null) onOutput(data = null) else {
                    _dataGet()?.let { data -> return onOutput(data) }

                    val new = if (len <= 0) {
                        Output.Data.empty()
                    } else {
                        buf.copyOf(newSize = len).asOutputData()
                    }
                    _dataSet(new)
                    onOutput(data = new)
                }

                // Output.Feed is expect/actual, so compiler cries. This satisfies it.
                else -> error("Unknown Output.Feed type ${this::class}")
            }
        }

        private inline val _this: Process get() = this as Process

        private inline fun stdoutVararg(feeds: Array<out Output.Feed>): Process {
            if (feeds.isEmpty()) return _this
            return addStdoutFeeds(feeds.toMutableSet())
        }

        private inline fun stderrVararg(feeds: Array<out Output.Feed>): Process {
            if (feeds.isEmpty()) return _this
            return addStderrFeeds(feeds.toMutableSet())
        }

        @Throws(Throwable::class)
        @OptIn(ExperimentalContracts::class)
        private inline fun <T> dispatch(
            thing: T,
            onErrorContext: String,
            feedsLock: Lock?,
            _onFeed: Output.Feed.(thing: T) -> Unit,
            _feedsGet: () -> Array<Output.Feed>,
            _feedsSet: (new: Array<Output.Feed>) -> Unit,
            _stoppedSet: (new: Boolean) -> Unit,
        ) {
            contract {
                callsInPlace(_onFeed, InvocationKind.UNKNOWN)
                callsInPlace(_feedsGet, InvocationKind.AT_LEAST_ONCE)
                callsInPlace(_feedsSet, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_stoppedSet, InvocationKind.AT_MOST_ONCE)
            }

            var threw: Throwable? = null

            var i = 0
            var feeds = _feedsGet()
            while (i < feeds.size) {
                val feed = feeds[i++]
                try {
                    feed._onFeed(thing)
                } catch (t: Throwable) {
                    threw?.addSuppressed(t) ?: run { threw = t }
                }

                // Array of Output.Feed only ever grows, so upon each iteration
                // can set locally in case additional Output.Feed were picked up
                // while dispatching this thing.
                feeds = _feedsGet()
            }

            threw?.let { t ->
                try {
                    onError(t, context = onErrorContext)
                    // Handler swallowed it
                    threw = null
                } catch (t: Throwable) {
                    threw = t
                }
            }

            if (threw == null) {
                if (thing != null) return
            }

            // thing was null (end of stream), or error. Close up shop.
            feedsLock?.withLock {
                _feedsSet(emptyArray())
                _stoppedSet(true)
                for (feed in feeds) {
                    if (feed !is Utf8LineDispatcher) continue
                    feed.close()
                    break
                }
            }
            threw?.let { throw it }
        }

        private fun addStdoutFeeds(feeds: MutableSet<Output.Feed>): Process = addFeeds(
            feedsAdd = feeds,
            feedsLock = stdoutLock,
            _dispatchLine = ::dispatchStdoutLine,
            _feedsGet = { _stdoutFeeds },
            _feedsSet = { new -> _stdoutFeeds = new },
            _isStopped = { _stdoutStopped },
            _startStdio = { _stdoutStarted = true; startStdout() },
        )

        private fun addStderrFeeds(feeds: MutableSet<Output.Feed>): Process = addFeeds(
            feedsAdd = feeds,
            feedsLock = stderrLock,
            _dispatchLine = ::dispatchStderrLine,
            _feedsGet = { _stderrFeeds },
            _feedsSet = { new -> _stderrFeeds = new },
            _isStopped = { _stderrStopped },
            _startStdio = { _stderrStarted = true; startStderr() },
        )

        @OptIn(ExperimentalContracts::class)
        private inline fun addFeeds(
            feedsAdd: MutableSet<Output.Feed>,
            feedsLock: Lock?,
            noinline _dispatchLine: (line: String?) -> Unit,
            _feedsGet: () -> Array<Output.Feed>,
            _feedsSet: (new: Array<Output.Feed>) -> Unit,
            _isStopped: () -> Boolean,
            _startStdio: () -> Unit,
        ): Process {
            contract {
                callsInPlace(_feedsGet, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_feedsSet, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_isStopped, InvocationKind.UNKNOWN)
                callsInPlace(_startStdio, InvocationKind.AT_MOST_ONCE)
            }

            if (feedsLock == null) return _this
            if (feedsAdd.isEmpty()) return _this
            if (isDestroyed) return _this
            if (_isStopped()) return _this

            val startStdio: Boolean = feedsLock.withLock {
                if (isDestroyed) return _this
                if (_isStopped()) return _this

                val feedsBefore = _feedsGet()

                var hasLineDispatcher = false
                for (i in feedsBefore.indices) {
                    val feed = feedsBefore[i]
                    if (feed is Utf8LineDispatcher) {
                        hasLineDispatcher = true
                        continue
                    }
                    if (feedsAdd.remove(feed) && feedsAdd.isEmpty()) return _this
                }

                if (isDestroyed) return _this

                var newSize = feedsBefore.size + feedsAdd.size
                val needsLineDispatcher = if (!hasLineDispatcher) run {
                    for (feed in feedsAdd) {
                        if (feed !is OutputFeed) continue
                        newSize++
                        return@run true
                    }
                    false
                } else {
                    false
                }

                val feedsAfter = feedsBefore.copyOf(newSize)
                var i = feedsBefore.size
                if (needsLineDispatcher) {
                    feedsAfter[i++] = Utf8LineDispatcher.of(_dispatchLine)
                }
                feedsAdd.forEach { feed -> feedsAfter[i++] = feed }

                @Suppress("UNCHECKED_CAST")
                _feedsSet(feedsAfter as Array<Output.Feed>)

                feedsBefore.isEmpty()
            }

            if (startStdio) _startStdio()
            return _this
        }

        // Exposed for testing
        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = _stdoutFeeds.size
        // Exposed for testing
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = _stderrFeeds.size

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stdout which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stdout(OutputFeed(feed))"),
            level = DeprecationLevel.WARNING,
        )
        public fun stdoutFeed(feed: OutputFeed): Process = stdout(feed)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stdout which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stdout(*feeds)"),
            level = DeprecationLevel.WARNING,
        )
        public fun stdoutFeed(vararg feeds: OutputFeed): Process = stdoutVararg(feeds)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stdout which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stdout(feeds)"),
            level = DeprecationLevel.WARNING,
        )
        public fun stdoutFeed(feeds: List<OutputFeed>): Process = stdout(feeds)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stderr which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stderr(OutputFeed(feed))"),
            level = DeprecationLevel.WARNING,
        )
        public fun stderrFeed(feed: OutputFeed): Process = stderr(feed)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stderr which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stderr(*feeds)"),
            level = DeprecationLevel.WARNING,
        )
        public fun stderrFeed(vararg feeds: OutputFeed): Process = stderrVararg(feeds)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced with stderr which accepts OutputFeed and OutputFeed.Raw.",
            replaceWith = ReplaceWith("stderr(feeds)"),
            level = DeprecationLevel.WARNING,
        )
        public fun stderrFeed(feeds: List<OutputFeed>): Process = stderr(feeds)

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced by Utf8LineDispatcher via OutputFeed.Raw. Now throws exception.",
            level = DeprecationLevel.ERROR,
        )
        @Suppress("UNUSED_PARAMETER")
        protected fun dispatchStdout(line: String?) {
            // It's a protected function, but to maintain binary compatibility.
            throw IllegalStateException()
        }

        /**
         * DEPRECATED since `0.6.0`
         * @suppress
         * */
        @Deprecated(
            message = "Replaced by Utf8LineDispatcher via OutputFeed.Raw. Now throws exception.",
            level = DeprecationLevel.ERROR,
        )
        @Suppress("UNUSED_PARAMETER")
        protected fun dispatchStderr(line: String?) {
            // It's a protected function, but to maintain binary compatibility.
            throw IllegalStateException()
        }
    }

    /**
     * A helper to wait for `stdout` and `stderr` stream output completion, after [Process.destroy] has
     * been called. Completion of [awaitStopAsync] (or `awaitStop` for Jvm & Native) guarantees that the
     * I/O stream being monitored has ended, and all [Output.Feed] attached to the respective stream have
     * been invoked and dereferenced by [Handler].
     *
     * @see [Handler.stdoutWaiter]
     * @see [Handler.stderrWaiter]
     * */
    public sealed class Waiter
    @Throws(IllegalStateException::class)
    protected constructor(process: Process, isDestroyed: Boolean): Blocking.Waiter(process) {

        /**
         * Delays the current coroutine until the [Stdio.Pipe] stops producing output.
         *
         * Does nothing if:
         *  - [Stdio] was **not** [Stdio.Pipe] for the I/O stream being monitored
         *  - No [Output.Feed] were attached to the I/O stream being monitored, before [Process.destroy]
         *    was called (i.e. never started)
         *  - The I/O stream being monitored has already stopped
         *
         * See: [Blocking.Waiter.awaitStop](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/-blocking/-waiter/await-stop.html)
         *
         * @return The [Process] for chaining operations.
         *
         * @throws [CancellationException]
         * */
        public suspend fun awaitStopAsync(): Process {
            while (isStarted() && !isStopped()) {
                delay(5.milliseconds)
            }

            return process
        }

        init {
            check(isDestroyed) { "Process.destroy must be called before an OutputFeed.Waiter can be created" }
        }
    }
}

private abstract class RealWaiter(process: Process, isDestroyed: Boolean): OutputFeed.Waiter(process, isDestroyed)
