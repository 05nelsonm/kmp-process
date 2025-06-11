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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "RemoveRedundantQualifierName")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDERR
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_FEED_STDOUT
import io.matthewnelson.kmp.process.internal.SynchronizedSet
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
 *     val p = builder.spawn()
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
    public sealed class Handler(private val stdio: Stdio.Config): Blocking() {

        @JvmField
        @Volatile
        protected var isDestroyed: Boolean = false
        @Volatile
        private var stdoutStarted: Boolean = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var stdoutStopped: Boolean = stdio.stdout !is Stdio.Pipe
        @Volatile
        private var stderrStarted: Boolean = stdio.stderr !is Stdio.Pipe
        @Volatile
        private var stderrStopped: Boolean = stdio.stderr !is Stdio.Pipe

        private val stdoutFeeds = SynchronizedSet<OutputFeed>()
        private val stderrFeeds = SynchronizedSet<OutputFeed>()

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
        public fun stdoutFeed(
            feed: OutputFeed,
        ): Process = stdoutFeed(*Array(1) { feed })

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
        public fun stdoutFeed(
            vararg feeds: OutputFeed,
        ): Process = stdoutFeeds.addFeeds(
            feeds,
            stdio.stdout,
            isStopped = { stdoutStopped },
            startStdio = { stdoutStarted = true; startStdout() },
        )

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
        public fun stderrFeed(
            feed: OutputFeed,
        ): Process = stderrFeed(*Array(1) { feed })

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
        public fun stderrFeed(
            vararg feeds: OutputFeed,
        ): Process = stderrFeeds.addFeeds(
            feeds,
            stdio.stderr,
            isStopped = { stderrStopped },
            startStdio = { stderrStarted = true; startStderr() },
        )

        /**
         * Returns a [Waiter] for `stdout` in order to await any
         * final asynchronous output after resource closure occurs.
         *
         * @throws [IllegalStateException] if [Process.destroy] has
         *   not been called yet.
         * */
        @Throws(IllegalStateException::class)
        public fun stdoutWaiter(): OutputFeed.Waiter {
            return object : RealWaiter() {
                override fun isStarted(): Boolean = stdoutStarted
                override fun isStopped(): Boolean = stdoutStopped
            }
        }

        /**
         * Returns a [Waiter] for `stderr` in order to await any
         * final asynchronous output after resource closure occurs.
         *
         * @throws [IllegalStateException] if [Process.destroy] has
         *   not been called yet.
         * */
        @Throws(IllegalStateException::class)
        public fun stderrWaiter(): OutputFeed.Waiter {
            return object : RealWaiter() {
                override fun isStarted(): Boolean = stderrStarted
                override fun isStopped(): Boolean = stderrStopped
            }
        }

        /** @suppress */
        protected fun dispatchStdout(line: String?) {
            stdoutFeeds.dispatch(
                line,
                onErrorContext = CTX_FEED_STDOUT,
                onClosed = { stdoutStopped = true },
            )
        }
        /** @suppress */
        protected fun dispatchStderr(line: String?) {
            stderrFeeds.dispatch(
                line,
                onErrorContext = CTX_FEED_STDERR,
                onClosed = { stderrStopped = true },
            )
        }

        /** @suppress */
        @Throws(Throwable::class)
        protected abstract fun onError(t: Throwable, context: String)
        /** @suppress */
        protected abstract fun startStdout()
        /** @suppress */
        protected abstract fun startStderr()

        @Suppress("NOTHING_TO_INLINE")
        @OptIn(ExperimentalContracts::class)
        private inline fun SynchronizedSet<OutputFeed>.addFeeds(
            feeds: Array<out OutputFeed>,
            stdio: Stdio,
            isStopped: () -> Boolean,
            startStdio: () -> Unit,
        ): Process {
            contract {
                callsInPlace(isStopped, InvocationKind.UNKNOWN)
                callsInPlace(startStdio, InvocationKind.AT_MOST_ONCE)
            }

            if (isDestroyed) return This
            if (feeds.isEmpty()) return This
            if (stdio !is Stdio.Pipe) return This
            if (isStopped()) return This

            val start = withLock {
                if (isDestroyed) return@withLock false
                if (isStopped()) return@withLock false
                val wasEmpty = isEmpty()
                feeds.forEach { add(it) }
                wasEmpty && isNotEmpty()
            }

            if (start) startStdio()

            return This
        }

        private abstract inner class RealWaiter: OutputFeed.Waiter(This, isDestroyed)

        @Suppress("NOTHING_TO_INLINE", "PrivatePropertyName")
        private inline val This: Process get() = this as Process

        @Suppress("NOTHING_TO_INLINE")
        @OptIn(ExperimentalContracts::class)
        private inline fun SynchronizedSet<OutputFeed>.dispatch(
            line: String?,
            onErrorContext: String,
            onClosed: () -> Unit,
        ) {
            contract {
                callsInPlace(onClosed, InvocationKind.AT_MOST_ONCE)
            }

            var threw: Throwable? = null

            withLock { toSet() }.forEach { feed ->
                try {
                    feed.onOutput(line)
                } catch (t: Throwable) {
                    threw?.addSuppressed(t) ?: run { threw = t }
                }
            }

            threw?.let { t ->
                try {
                    onError(t, context = onErrorContext)
                    // Handler swallowed it
                    threw = null
                } catch (e: Throwable) {
                    threw = e
                }
            }

            if (threw == null) {
                if (line != null) return
            }

            // Line was null (end of stream), or error. Close up shop.
            withLock { clear(); onClosed() }
            threw?.let { throw it }
        }

        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = stdoutFeeds.withLock { size }
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = stderrFeeds.withLock { size }
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
         * **NOTE:** For Jvm & Android the `kotlinx.coroutines.core`
         * dependency is needed.
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
