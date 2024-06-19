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
package io.matthewnelson.kmp.process

import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Represents an error that has been encountered during the
 * runtime of a [Process].
 *
 * This API is available to provide `kmp-process` consumers
 * the choice of how internal errors get handled, as well as
 * `kmp-process` the ability to react to that choice in a
 * manner which prevents zombie processes.
 *
 * @see [Handler]
 * */
public class ProcessException private constructor(

    /**
     * Contextual information about where the error occurred.
     *
     * For example, a `context` of [CTX_DESTROY] indicates that
     * the [cause] was encountered when [Process.destroy] was
     * called. A `context` of [CTX_FEED_STDOUT] indicates that
     * an [OutputFeed] attached to stdout threw an exception when
     * a line was dispatched to it.
     * */
    @JvmField
    public val context: String,

    /**
     * The underlying cause of the [ProcessException].
     * */
    public override val cause: Throwable,
): RuntimeException(context, cause) {

    public override val message: String = "context: $context"

    /**
     * A callback for being notified of [ProcessException], if
     * they occur.
     * */
    public fun interface Handler {

        /**
         * If a [Handler] implementation throws an exception from
         * within its [onException] lambda, the [Process] will be terminated.
         *
         * **WARNING:** Throwing an exception may result in a crash.
         * */
        @Throws(Throwable::class)
        public fun onException(e: ProcessException)

        public companion object {

            /**
             * Static instance that swallows (ignores) the [ProcessException].
             *
             * This is the default used by [Process.Builder].
             * */
            @JvmField
            public val IGNORE: Handler = object : Handler {
                override fun onException(e: ProcessException) {}
                override fun toString(): String = "ProcessException.Handler.IGNORE"
            }
        }
    }

    public companion object {

        /**
         * String for [ProcessException.context] indicating that
         * [Process.destroy] threw an exception when it was called.
         * */
        public const val CTX_DESTROY: String = "destroy"

        /**
         * String for [ProcessException.context] indicating that
         * an [OutputFeed] for [Stdio.Config.stdout] threw exception
         * when [OutputFeed.onOutput] was called.
         * */
        public const val CTX_FEED_STDERR: String = "feed.stderr"

        /**
         * String for [ProcessException.context] indicating that
         * an [OutputFeed] for [Stdio.Config.stderr] threw exception
         * when [OutputFeed.onOutput] was called.
         * */
        public const val CTX_FEED_STDOUT: String = "feed.stdout"

        @JvmSynthetic
        internal fun of(
            context: String,
            cause: Throwable,
        ): ProcessException {
            if (cause is ProcessException) return cause
            return ProcessException(context, cause)
        }
    }
}
