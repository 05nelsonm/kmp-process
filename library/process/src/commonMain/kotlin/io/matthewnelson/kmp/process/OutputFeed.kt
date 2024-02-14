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

import io.matthewnelson.kmp.process.internal.SynchronizedSet
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

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
 *             },
 *         )
 *
 * @see [Handler.stdoutFeed]
 * @see [Handler.stderrFeed]
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
        public fun stdoutFeed(feed: OutputFeed): Process {
            return stdoutFeed(*arrayOf(feed))
        }

        /**
         * Attaches multiple [OutputFeed] to obtain `stdout` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to being attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stdout` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stdout] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stdoutFeed(vararg feeds: OutputFeed): Process {
            return stdoutFeeds.addFeeds(feeds, stdio.stdout, ::startStdout)
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
        public fun stderrFeed(feed: OutputFeed): Process {
            return stderrFeed(*arrayOf(feed))
        }

        /**
         * Attaches multiple [OutputFeed] to obtain `stderr` output.
         * This is handy at [Process] startup such that no data is
         * missed if there are multiple feeds needing to being attached.
         *
         * [Process] will begin outputting data to all [OutputFeed]
         * for `stderr` upon the first attachment of [OutputFeed].
         *
         * If [Stdio.Config.stderr] is **not** [Stdio.Pipe], this
         * does nothing. If the [Process] has been destroyed, this
         * does nothing.
         * */
        public fun stderrFeed(vararg feeds: OutputFeed): Process {
            return stderrFeeds.addFeeds(feeds, stdio.stderr, ::startStderr)
        }

        protected fun dispatchStdout(line: String) {
            stdoutFeeds.withLock {
                forEach { feed ->
                    try {
                        feed.onOutput(line)
                    } catch (_: Throwable) {}
                }
            }
        }

        protected fun dispatchStderr(line: String) {
            stderrFeeds.withLock {
                forEach { feed ->
                    try {
                        feed.onOutput(line)
                    } catch (_: Throwable) {}
                }
            }
        }

        protected fun onStdoutStopped() {
            stdoutFeeds.withLock { clear() }
        }
        protected fun onStderrStopped() {
            stderrFeeds.withLock { clear() }
        }

        protected abstract fun startStdout()
        protected abstract fun startStderr()

        private fun SynchronizedSet<OutputFeed>.addFeeds(
            feeds: Array<out OutputFeed>,
            stdio: Stdio,
            startStdio: () -> Unit
        ): Process {
            if (feeds.isEmpty()) return This
            if (isDestroyed) return This
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

        @JvmSynthetic
        internal fun stdoutFeedsSize(): Int = stdoutFeeds.withLock { size }
        @JvmSynthetic
        internal fun stderrFeedsSize(): Int = stderrFeeds.withLock { size }
    }
}
