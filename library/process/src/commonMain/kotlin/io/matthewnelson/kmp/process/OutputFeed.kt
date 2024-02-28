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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.internal.SynchronizedSet
import io.matthewnelson.kmp.process.internal.threadSleep
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
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
 * **NOTE:** Any uncaught exceptions that [onOutput]
 * throws will be swallowed.
 *
 * e.g.
 *
 *     val p = builder.spawn()
 *         .stdoutFeed { line ->
 *             println(line)
 *         }.stderrFeed(
 *             // attach multiple at once
 *             OutputFeed { line ->
 *                 println(line)
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
     * */
    public fun onOutput(line: String)

    /**
     * Helper class which [Process] implements that handles everything
     * regarding dispatching of `stdout` and `stderr` output to attached
     * [OutputFeed].
     *
     * Upon [Process] destruction, all attached [OutputFeed] are ejected.
     * */
    public sealed class Handler(private val stdio: Stdio.Config) {

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
        ): Process = stdoutFeeds.addFeeds(feeds, stdio.stdout, startStdio = {
            stdoutStarted = true
            startStdout()
        })

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
        ): Process = stderrFeeds.addFeeds(feeds, stdio.stderr, startStdio = {
            stderrStarted = true
            startStderr()
        })

        /**
         * Returns a [Waiter] for `stdout` in order to await any
         * final asynchronous output after resource closure occurs.
         *
         * @throws [IllegalStateException] if [Process.destroy] has
         *   not been called yet.
         * */
        @Throws(IllegalStateException::class)
        public fun stdoutWaiter(): Waiter {
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
        public fun stderrWaiter(): Waiter {
            return object : RealWaiter() {
                override fun isStarted(): Boolean = stderrStarted
                override fun isStopped(): Boolean = stderrStopped
            }
        }

        protected fun dispatchStdout(line: String) { stdoutFeeds.dispatch(line) }
        protected fun dispatchStderr(line: String) { stderrFeeds.dispatch(line) }

        protected fun onStdoutStopped() { stdoutFeeds.withLock { clear(); stdoutStopped = true } }
        protected fun onStderrStopped() { stderrFeeds.withLock { clear(); stderrStopped = true } }

        protected abstract fun startStdout()
        protected abstract fun startStderr()

        private fun SynchronizedSet<OutputFeed>.addFeeds(
            feeds: Array<out OutputFeed>,
            stdio: Stdio,
            startStdio: () -> Unit
        ): Process {
            if (isDestroyed) return This
            if (feeds.isEmpty()) return This
            if (stdio !is Stdio.Pipe) return This

            val start = withLock {
                if (isDestroyed) return@withLock false
                val wasEmpty = isEmpty()
                feeds.forEach { add(it) }
                wasEmpty && isNotEmpty()
            }

            if (start) startStdio()

            return This
        }

        @Suppress("NOTHING_TO_INLINE", "PrivatePropertyName")
        private inline val This: Process get() = this as Process

        @Suppress("NOTHING_TO_INLINE")
        private inline fun SynchronizedSet<OutputFeed>.dispatch(line: String) {
            withLock {
                forEach { feed ->
                    try {
                        feed.onOutput(line)
                    } catch (_: Throwable) {}
                }
            }
        }

        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = stdoutFeeds.withLock { size }
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = stderrFeeds.withLock { size }

        private abstract inner class RealWaiter: Waiter(This, isDestroyed)
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
        private val process: Process,
        isDestroyed: Boolean,
    ) {

        /**
         * Blocks the current thread until the [Stdio.Pipe]
         * stops producing output.
         *
         * Does nothing if:
         *  - Stdio was not [Stdio.Pipe]
         *  - No [OutputFeed] were attached before [Process.destroy]
         *    was called (i.e. never started)
         *  - Has already stopped
         *
         * @return [Process] for chaining calls
         * @throws [InterruptedException]
         * @throws [UnsupportedOperationException] on Node.js
         * */
        @Throws(InterruptedException::class, UnsupportedOperationException::class)
        public fun awaitStop(): Process {
            while (isStarted() && !isStopped()) {
                5.milliseconds.threadSleep()
            }

            return process
        }

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
         * **NOTE:** This API requires the `kotlinx.coroutines` core
         * dependency (at a minimum) in order to pass in the
         * `kotlinx.coroutines.delay` function. Adding the dependency
         * to `kmp-process` for a single function to use in an API
         * that may not even be utilized (because [awaitStop] exists for
         * non-JS) seemed ridiculous.
         *
         * e.g.
         *
         *     myDestroyedProcess.stdoutWaiter()
         *         .awaitStopAsync(::delay)
         *
         * @return [Process] for chaining calls
         * */
        public suspend fun awaitStopAsync(
            delay: suspend (duration: Duration) -> Unit
        ): Process {
            while (isStarted() && !isStopped()) {
                delay(5.milliseconds)
            }

            return process
        }

        protected abstract fun isStarted(): Boolean
        protected abstract fun isStopped(): Boolean

        init {
            check(isDestroyed) { "Process.destroy must be called before an OutputFeed.Waiter can be created" }
        }
    }
}
