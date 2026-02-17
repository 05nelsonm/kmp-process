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
import io.matthewnelson.kmp.process.internal.Lock
import io.matthewnelson.kmp.process.internal.newLock
import io.matthewnelson.kmp.process.internal.withLock
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds

/**
 * A callback for obtaining `stdout` and `stderr` output.
 *
 * **NOTE:** `Jvm` and `Native` [OutputFeed.onOutput] and [OutputFeed.Raw.onOutput] are called from a
 * background thread; either the `stdout` thread or the `stderr` thread. It is ill-advised to register
 * the same [Output.Feed] to both `stdout` and `stderr`.
 *
 * **NOTE:** Any exceptions that [OutputFeed.onOutput] or [OutputFeed.Raw.onOutput] throws will be
 * delegated to [ProcessException.Handler]. If it is re-thrown by the [ProcessException.Handler], the
 * [Process] will be terminated, and all [OutputFeed] for that I/O stream will be ejected immediately.
 *
 * e.g.
 *
 *     TODO
 *
 * */
public fun interface OutputFeed: Output.Feed {

    /**
     * TODO
     * */
    public fun onOutput(line: String?)

    /**
     * TODO
     * */
    public fun interface Raw: Output.Feed {

        /**
         * TODO
         * */
        public fun onOutput(len: Int, get: (index: Int) -> Byte)
    }

    /**
     * TODO
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
         * TODO
         * */
        public fun stdout(feed: Output.Feed): Process {
            return addStdoutFeeds(mutableSetOf(feed))
        }

        /**
         * TODO
         * */
        public fun stdout(vararg feeds: Output.Feed): Process = stdoutVararg(feeds)

        /**
         * TODO
         * */
        public fun stdout(feeds: Collection<Output.Feed>): Process {
            if (feeds.isEmpty()) return This
            return addStdoutFeeds(feeds.toMutableSet())
        }

        /**
         * TODO
         * */
        public fun stderr(feed: Output.Feed): Process {
            return addStderrFeeds(mutableSetOf(feed))
        }

        /**
         * TODO
         * */
        public fun stderr(vararg feeds: Output.Feed): Process = stderrVararg(feeds)

        /**
         * TODO
         * */
        public fun stderr(feeds: Collection<Output.Feed>): Process {
            if (feeds.isEmpty()) return This
            return addStderrFeeds(feeds.toMutableSet())
        }

        /**
         * TODO
         * */
        @Throws(IllegalStateException::class)
        public fun stdoutWaiter(): OutputFeed.Waiter = object : RealWaiter(This, isDestroyed) {
            override fun isStarted(): Boolean = _stdoutStarted
            override fun isStopped(): Boolean = _stdoutStopped
        }

        /**
         * TODO
         * */
        @Throws(IllegalStateException::class)
        public fun stderrWaiter(): OutputFeed.Waiter = object : RealWaiter(This, isDestroyed) {
            override fun isStarted(): Boolean = _stderrStarted
            override fun isStopped(): Boolean = _stderrStopped
        }

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

        /** @suppress */
        protected fun dispatchStdout(line: String?) {
            dispatch(
                line = line,
                onErrorContext = CTX_FEED_STDOUT,
                feedsLock = stdoutLock,
                _feedsGet = { _stdoutFeeds },
                _feedsSet = { new -> _stdoutFeeds = new },
                _stoppedSet = { new -> _stdoutStopped = new }
            )
        }

        /** @suppress */
        protected fun dispatchStderr(line: String?) {
            dispatch(
                line = line,
                onErrorContext = CTX_FEED_STDERR,
                feedsLock = stderrLock,
                _feedsGet = { _stderrFeeds },
                _feedsSet = { new -> _stderrFeeds = new },
                _stoppedSet = { new -> _stderrStopped = new }
            )
        }

        /** @suppress */
        @Throws(Throwable::class)
        protected abstract fun onError(t: Throwable, context: String)
        /** @suppress */
        protected abstract fun startStdout()
        /** @suppress */
        protected abstract fun startStderr()

        @Suppress("PrivatePropertyName")
        private inline val This: Process get() = this as Process

        private inline fun stdoutVararg(feeds: Array<out Output.Feed>): Process {
            if (feeds.isEmpty()) return This
            return addStdoutFeeds(feeds.toMutableSet())
        }

        private inline fun stderrVararg(feeds: Array<out Output.Feed>): Process {
            if (feeds.isEmpty()) return This
            return addStderrFeeds(feeds.toMutableSet())
        }

        @OptIn(ExperimentalContracts::class)
        private inline fun dispatch(
            line: String?,
            onErrorContext: String,
            feedsLock: Lock?,
            _feedsGet: () -> Array<Output.Feed>,
            _feedsSet: (new: Array<Output.Feed>) -> Unit,
            _stoppedSet: (new: Boolean) -> Unit,
        ) {
            contract {
                callsInPlace(_feedsGet, InvocationKind.AT_LEAST_ONCE)
                callsInPlace(_feedsSet, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_stoppedSet, InvocationKind.AT_MOST_ONCE)
            }

            var threw: Throwable? = null

            var i = 0
            var feeds = _feedsGet()
            while (i < feeds.size) {
                val feed = feeds[i++]
                if (feed !is OutputFeed) continue
                try {
                    feed.onOutput(line)
                } catch (t: Throwable) {
                    threw?.addSuppressed(t) ?: run { threw = t }
                }

                // Array of OutputFeed only ever grows, so upon each iteration
                // can set locally in case additional OutputFeed were picked up
                // while dispatching this line.
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
                if (line != null) return
            }

            // Line was null (end of stream), or error. Close up shop.
            feedsLock?.withLock {
                _feedsSet(emptyArray())
                _stoppedSet(true)
            }
            threw?.let { throw it }
        }

        private fun addStdoutFeeds(feeds: MutableSet<Output.Feed>): Process = addFeeds(
            feedsAdd = feeds,
            feedsLock = stdoutLock,
            _feedsGet = { _stdoutFeeds },
            _feedsSet = { new -> _stdoutFeeds = new },
            _isStopped = { _stdoutStopped },
            _startStdio = { _stdoutStarted = true; startStdout() },
        )

        private fun addStderrFeeds(feeds: MutableSet<Output.Feed>): Process = addFeeds(
            feedsAdd = feeds,
            feedsLock = stderrLock,
            _feedsGet = { _stderrFeeds },
            _feedsSet = { new -> _stderrFeeds = new },
            _isStopped = { _stderrStopped },
            _startStdio = { _stderrStarted = true; startStderr() },
        )

        @OptIn(ExperimentalContracts::class)
        private inline fun addFeeds(
            feedsAdd: MutableSet<Output.Feed>,
            feedsLock: Lock?,
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

            if (feedsLock == null) return This
            if (feedsAdd.isEmpty()) return This
            if (isDestroyed) return This
            if (_isStopped()) return This

            val startStdio: Boolean = feedsLock.withLock {
                if (isDestroyed) return This
                if (_isStopped()) return This

                val feedsBefore = _feedsGet()

                for (i in feedsBefore.indices) {
                    val feed = feedsBefore[i]
                    if (feedsAdd.remove(feed) && feedsAdd.isEmpty()) return This
                }

                if (isDestroyed) return This

                val feedsAfter = feedsBefore.copyOf(feedsBefore.size + feedsAdd.size)
                var i = feedsBefore.size
                feedsAdd.forEach { feed -> feedsAfter[i++] = feed }

                @Suppress("UNCHECKED_CAST")
                _feedsSet(feedsAfter as Array<Output.Feed>)

                feedsBefore.isEmpty()
            }

            if (startStdio) _startStdio()
            return This
        }

        // Exposed for testing
        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = _stdoutFeeds.size
        // Exposed for testing
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = _stderrFeeds.size
    }

    /**
     * A helper to wait for `stdout` and `stderr` asynchronous
     * output to stop after [Process.destroy] has been called.
     *
     * @see [Handler.stdoutWaiter]
     * @see [Handler.stderrWaiter]
     * */
    public sealed class Waiter
    @Throws(IllegalStateException::class)
    protected constructor(
        process: Process,
        isDestroyed: Boolean,
    ): Blocking.Waiter(process) {

        /**
         * Delays the current coroutine until the [Stdio.Pipe]
         * stops producing output.
         *
         * Does nothing if:
         *  - Stdio was not [Stdio.Pipe]
         *  - No [OutputFeed] were attached before [Process.destroy]
         *    was called (i.e. never started)
         *  - Has already stopped
         *
         * See: [Blocking.Waiter.awaitStop](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/-blocking/-waiter/await-stop.html)
         *
         * @return [Process] for chaining calls
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
