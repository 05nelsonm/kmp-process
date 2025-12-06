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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.internal.commonWaitFor
import io.matthewnelson.kmp.process.internal.threadSleep
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extended by [OutputFeed.Handler] (which is extended by [Process]) in order to provide
 * blocking APIs for Jvm & Native.
 *
 * @see [threadSleep]
 * */
public actual sealed class Blocking protected actual constructor() {

    /**
     * Blocks the current thread until [Process] completion.
     *
     * @return The [Process.exitCode]
     * @throws [InterruptedException]
     * */
    @Throws(InterruptedException::class)
    public fun waitFor(): Int {
        var exitCode: Int? = null
        while (exitCode == null) {
            exitCode = waitFor(Duration.INFINITE)
        }
        return exitCode
    }

    /**
     * Blocks the current thread for the specified [duration],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [duration] the [Duration] to wait
     * @return The [Process.exitCode], or null if [duration] is
     *   exceeded without [Process] completion.
     * @throws [InterruptedException]
     * */
    @Throws(InterruptedException::class)
    public fun waitFor(
        duration: Duration,
    ): Int? = (this as Process).commonWaitFor(duration) { millis -> millis.threadSleep() }

    public companion object {

        /**
         * Helper function for Jvm & Native for blocking the thread for
         * the specified [Duration]
         *
         * e.g.
         *
         *     Blocking.threadSleep(50.milliseconds)
         *
         * @throws [IllegalArgumentException] when [Duration] is improper
         * @throws [InterruptedException] if the calling thread was interrupted
         * */
        @JvmStatic
        @Throws(InterruptedException::class)
        public fun threadSleep(duration: Duration) { duration.threadSleep() }
    }

    /**
     * Extended by [OutputFeed.Waiter] in order to provide blocking APIs for Jvm & Native.
     * */
    public actual sealed class Waiter actual constructor(
        /** @suppress */
        @JvmField
        protected actual val process: Process,
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
         * */
        @Throws(InterruptedException::class)
        public fun awaitStop(): Process {
            while (isStarted() && !isStopped()) {
                5.milliseconds.threadSleep()
            }

            return process
        }

        /** @suppress */
        protected actual abstract fun isStarted(): Boolean
        /** @suppress */
        protected actual abstract fun isStopped(): Boolean
    }

    /**
     * Extended by [Process.Builder] in order to provide blocking APIs for Jvm/Native
     * */
    public actual sealed class Builder protected actual constructor() {

        /**
         * Create the [Process] synchronously.
         *
         * @see [Process.Builder.createProcessAsync]
         * */
        @Throws(IOException::class)
        public fun createProcess(): Process = createProcessProtected()

        /** @suppress */
        @Throws(IOException::class)
        protected actual abstract fun createProcessProtected(): Process
    }
}
