/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.Process
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

internal typealias Condition<T> = () -> T?

internal inline fun <T: Any> condition(noinline c: Condition<T>): Condition<T> = c

// @Throws(CancellationException::class, InterruptedException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun Process.commonWaitFor(
    timeout: Duration,
    sleep: (millis: Duration) -> Unit,
): Int? {
    contract { callsInPlace(sleep, InvocationKind.UNKNOWN) }
    return ::exitCodeOrNull.commonWaitFor(timeout, sleep)
}

// @Throws(CancellationException::class, InterruptedException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun <T: Any> Condition<T>.commonWaitFor(
    timeout: Duration,
    sleep: (millis: Duration) -> Unit,
): T? {
    contract { callsInPlace(sleep, InvocationKind.UNKNOWN) }

    val startMark = TimeSource.Monotonic.markNow()
    var remainingNanos = timeout.inWholeNanoseconds

    do {
        val condition = this.invoke()
        if (condition != null) return condition

        if (remainingNanos > 0) {
            val millis = minOf(
                (remainingNanos.nanoseconds.inWholeMilliseconds + 1).toDouble(),
                100.0
            ).toLong().milliseconds

            sleep(millis)
        }

        remainingNanos = (timeout - startMark.elapsedNow()).inWholeNanoseconds
    } while (remainingNanos > 0)

    return null
}
