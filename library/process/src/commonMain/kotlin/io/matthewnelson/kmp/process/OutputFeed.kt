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
@file:Suppress("LocalVariableName", "RedundantVisibilityModifier", "RemoveRedundantQualifierName")

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
 * **NOTE:** `Jvm` and `Native` [onOutput] is invoked
 * from a background thread (either the `stdout` thread or
 * the `stderr` thread, whichever is being observed by the
 * [OutputFeed]). It is ill-advised to attach the same
 * [OutputFeed] to both `stdout` and `stderr`.
 *
 * **NOTE:** Any exceptions that [onOutput] throws will be
 * delegated to [ProcessException.Handler]. If it is re-thrown
 * by the [ProcessException.Handler], the [Process] will be
 * terminated, and all [OutputFeed] for that I/O stream will
 * be ejected immediately.
 *
 * e.g.
 *
 *     val p = builder.createProcess()
 *         .stdoutFeed { line ->
 *             println(line ?: "--STDOUT EOS--")
 *         }.stderrFeed(
 *             // attach multiple at once
 *             OutputFeed { line ->
 *                 println(line ?: "--STDERR EOS--")
 *             },
 *             OutputFeed { line ->
 *                 // do something
 *             }
 *         )
 *
 *     p.waitFor(500.milliseconds)
 *
 *     val exitCode = p.destroy()
 *         .stdoutWaiter()
 *         .awaitStop()
 *         .stderrWaiter()
 *         .awaitStop()
 *         .waitFor()
 *
 * @see [Process.destroy]
 * @see [Handler.stdoutFeed]
 * @see [Handler.stderrFeed]
 * @see [Handler.stdoutWaiter]
 * @see [Handler.stderrWaiter]
 * */
public fun interface OutputFeed {

    /**
     * A line of output from `stdout` or `stderr` (whichever
     * this [OutputFeed] has been attached to).
     *
     * `null` is dispatched to indicate [OutputFeed] closure.
     * */
    public fun onOutput(line: String?)

    /**
     * Helper class which [Process] implements that handles everything
     * regarding dispatching of `stdout` and `stderr` output to attached
     * [OutputFeed].
     *
     * Upon [Process] destruction, all attached [OutputFeed] are ejected.
     * */
    public sealed class Handler(stdio: Stdio.Config): Blocking() {

        /** @suppress */
        @JvmField
        @Volatile
        protected var isDestroyed: Boolean = false

        @Volatile
        private var _stdoutStarted: Boolean = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var _stdoutStopped: Boolean = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var _stderrStarted: Boolean = stdio.stderr !is Stdio.Pipe
        @Volatile
        private var _stderrStopped: Boolean = stdio.stderr !is Stdio.Pipe

        private val stdoutLock = if (stdio.stdout !is Stdio.Pipe) null else newLock()
        private val stderrLock = if (stdio.stderr !is Stdio.Pipe) null else newLock()

        @Volatile
        private var _stdoutFeeds = if (stdoutLock == null) emptyArray() else arrayOfNulls<OutputFeed>(1)
        @Volatile
        private var _stderrFeeds = if (stderrLock == null) emptyArray() else arrayOfNulls<OutputFeed>(1)

        /**
         * Attaches a single [OutputFeed] to obtain `stdout` output.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stdout` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stdout] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stdoutFeed(feed: OutputFeed): Process = addStdoutFeeds(mutableSetOf(feed))

        /**
         * Attaches multiple [OutputFeed] to obtain `stdout` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to be attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stdout` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stdout] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stdoutFeed(vararg feeds: OutputFeed): Process {
            if (feeds.isEmpty()) return This
            return addStdoutFeeds(feeds.toMutableSet())
        }

        /**
         * Attaches multiple [OutputFeed] to obtain `stdout` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to be attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stdout` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stdout] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stdoutFeed(feeds: List<OutputFeed>): Process {
            if (feeds.isEmpty()) return This
            return addStdoutFeeds(feeds.toMutableSet())
        }

        /**
         * Attaches a single [OutputFeed] to obtain `stderr` output.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stderr` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stderr] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stderrFeed(feed: OutputFeed): Process = addStderrFeeds(mutableSetOf(feed))

        /**
         * Attaches multiple [OutputFeed] to obtain `stderr` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to be attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stderr` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stderr] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stderrFeed(vararg feeds: OutputFeed): Process {
            if (feeds.isEmpty()) return This
            return addStderrFeeds(feeds.toMutableSet())
        }

        /**
         * Attaches multiple [OutputFeed] to obtain `stderr` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to be attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stderr` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stderr] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stderrFeed(feeds: List<OutputFeed>): Process {
            if (feeds.isEmpty()) return This
            return addStderrFeeds(feeds.toMutableSet())
        }

        /**
         * Returns a [Waiter] for `stdout` in order to await any
         * final asynchronous output after resource closure occurs.
         *
         * @throws [IllegalStateException] if [Process.destroy] has
         *   not been called yet.
         * */
        @Throws(IllegalStateException::class)
        public fun stdoutWaiter(): OutputFeed.Waiter = object : RealWaiter(This, isDestroyed) {
            override fun isStarted(): Boolean = _stdoutStarted
            override fun isStopped(): Boolean = _stdoutStopped
        }

        /**
         * Returns a [Waiter] for `stderr` in order to await any
         * final asynchronous output after resource closure occurs.
         *
         * @throws [IllegalStateException] if [Process.destroy] has
         *   not been called yet.
         * */
        @Throws(IllegalStateException::class)
        public fun stderrWaiter(): OutputFeed.Waiter = object : RealWaiter(This, isDestroyed) {
            override fun isStarted(): Boolean = _stderrStarted
            override fun isStopped(): Boolean = _stderrStopped
        }

        /** @suppress */
        protected fun dispatchStdout(line: String?) {
            dispatch(
                line,
                onErrorContext = CTX_FEED_STDOUT,
                _feedsGet = { _stdoutFeeds },
                _onStopped = {
                    stdoutLock?.withLock {
                        _stdoutFeeds = emptyArray()
                        _stdoutStopped = true
                    }
                },
            )
        }

        /** @suppress */
        protected fun dispatchStderr(line: String?) {
            dispatch(
                line,
                onErrorContext = CTX_FEED_STDERR,
                _feedsGet = { _stderrFeeds },
                _onStopped = {
                    stderrLock?.withLock {
                        _stderrFeeds = emptyArray()
                        _stderrStopped = true
                    }
                },
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

        @OptIn(ExperimentalContracts::class)
        private inline fun dispatch(
            line: String?,
            onErrorContext: String,
            _feedsGet: () -> Array<OutputFeed?>,
            _onStopped: () -> Unit,
        ) {
            contract {
                callsInPlace(_feedsGet, InvocationKind.AT_LEAST_ONCE)
                callsInPlace(_onStopped, InvocationKind.AT_MOST_ONCE)
            }

            var threw: Throwable? = null

            var i = 0
            var feeds = _feedsGet()
            while (i < feeds.size) {
                val feed = feeds[i++] ?: break

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
            _onStopped()
            threw?.let { throw it }
        }

        private fun addStdoutFeeds(feeds: MutableSet<OutputFeed>): Process = addFeeds(
            newFeeds = feeds,
            feedsLock = stdoutLock,
            _feedsGet = { _stdoutFeeds },
            _feedsSet = { new -> _stdoutFeeds = new },
            _isStopped = { _stdoutStopped },
            _startStdio = { _stdoutStarted = true; startStdout() },
        )

        private fun addStderrFeeds(feeds: MutableSet<OutputFeed>): Process = addFeeds(
            newFeeds = feeds,
            feedsLock = stderrLock,
            _feedsGet = { _stderrFeeds },
            _feedsSet = { new -> _stderrFeeds = new },
            _isStopped = { _stderrStopped },
            _startStdio = { _stderrStarted = true; startStderr() },
        )

        @OptIn(ExperimentalContracts::class)
        private inline fun addFeeds(
            newFeeds: MutableSet<OutputFeed>,
            feedsLock: Lock?,
            _feedsGet: () -> Array<OutputFeed?>,
            _feedsSet: (new: Array<OutputFeed?>) -> Unit,
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
            if (newFeeds.isEmpty()) return This
            if (isDestroyed) return This
            if (_isStopped()) return This

            val startStdio: Boolean = feedsLock.withLock {
                if (isDestroyed) return@withLock false
                if (_isStopped()) return@withLock false

                var feeds = _feedsGet()
                if (feeds.isEmpty()) return This

                val wasEmpty = feeds[0] == null

                var iNext = 0
                while (iNext < feeds.size) {
                    val feed = feeds[iNext] ?: break

                    if (newFeeds.contains(feed)) {
                        newFeeds.remove(feed)
                        if (newFeeds.isEmpty()) return This
                    }

                    iNext++
                }

                if (isDestroyed) return This
                if (_isStopped()) return This

                val requiredSize = iNext + newFeeds.size
                val wasGrown = if (feeds.size < requiredSize) {
                    // Need to grow to accommodate new feeds
                    val newSize = (feeds.size + (feeds.size / 2)).coerceAtLeast(requiredSize)
                    feeds = feeds.copyOf(newSize)
                    true
                } else {
                    false
                }

                newFeeds.forEach { feed -> feeds[iNext++] = feed }
                if (wasGrown) _feedsSet(feeds)

                wasEmpty && feeds[0] != null
            }

            if (startStdio) _startStdio()
            return This
        }

        // Exposed for testing
        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = _stdoutFeeds.count { it != null }
        // Exposed for testing
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = _stderrFeeds.count { it != null }
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
