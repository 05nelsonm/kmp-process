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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Process
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Duration

internal actual val IsMobile: Boolean get() {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.ANDROID,
        OsFamily.IOS,
        OsFamily.TVOS,
        OsFamily.WATCHOS -> true
        else -> false
    }
}

internal inline fun NativeProcess.destroySuppressed(other: Throwable): Throwable {
    try {
        destroy()
    } catch (t: Throwable) {
        other.addSuppressed(t)
    }
    return other
}

@Throws(InterruptedException::class)
internal actual inline fun Duration.threadSleep() {
    if (isNegative()) throw IllegalArgumentException("duration cannot be negative")

    // usleep does not like durations greater than 1s
    // on some systems. Break it up over multiple calls.
    var remainingMicros: Long = inWholeMicroseconds
    while (remainingMicros > 1_000_000L) {
        if (usleep(999_999u) == -1) {
            // EINTR
            throw InterruptedException()
        }
        remainingMicros -= 1_000_000L
    }
    if (remainingMicros > 0) {
        if (usleep(remainingMicros.toUInt()) == -1) {
            // EINTR
            throw InterruptedException()
        }
    }
}

internal actual inline fun Process.wasStdoutThreadStarted(): Boolean = (this as NativeProcess).wasStdoutThreadStarted
internal actual inline fun Process.wasStderrThreadStarted(): Boolean = (this as NativeProcess).wasStderrThreadStarted

@OptIn(ExperimentalForeignApi::class)
internal fun List<String>.toArgv(
    command: String,
    scope: AutofreeScope,
): CArrayPointer<CPointerVar<ByteVar>> = with(scope) {
    val argv = allocArray<CPointerVar<ByteVar>>(size + 2)

    argv[0] = command.substringAfterLast(SysDirSep).cstr.getPointer(scope)

    var i = 1
    val iterator = iterator()
    while (iterator.hasNext()) {
        argv[i++] = iterator.next().cstr.getPointer(scope)
    }

    argv[i] = null

    argv
}

@OptIn(ExperimentalForeignApi::class)
internal fun Map<String, String>.toEnvp(
    scope: AutofreeScope,
): CArrayPointer<CPointerVar<ByteVar>> = with(scope) {
    val envp = allocArray<CPointerVar<ByteVar>>(size + 1)

    var i = 0
    val iterator = entries.iterator()
    while (iterator.hasNext()) {
        val (k, v) = iterator.next()
        envp[i++] = "$k=$v".cstr.getPointer(scope)
    }

    envp[i] = null

    envp
}

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun Int.check(
    condition: (result: Int) -> Boolean = { it >= 0 },
): Int {
    contract {
        callsInPlace(condition, InvocationKind.EXACTLY_ONCE)
    }

    if (condition(this)) return this
    @OptIn(ExperimentalForeignApi::class)
    throw errnoToIOException(errno)
}
