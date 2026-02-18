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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.Process
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.set
import platform.posix.errno
import platform.posix.getpid
import platform.posix.usleep
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Duration
import kotlin.time.TimeSource

internal actual val IsDesktop: Boolean get() {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.ANDROID,
        OsFamily.IOS,
        OsFamily.TVOS,
        OsFamily.WASM,
        OsFamily.WATCHOS -> false
        OsFamily.MACOSX,
        OsFamily.LINUX,
        OsFamily.WINDOWS -> true
        // Unknown, but this is experimental so if they ever
        // expand the enum with more options, we want to
        // mitigate the compile time error.
        else -> true
    }
}

@Throws(UnsupportedOperationException::class)
internal actual inline fun Process.Current.platformPID(): Int = getpid()

internal inline fun NativeProcess.destroySuppressed(other: Throwable): Throwable {
    try {
        destroy()
    } catch (t: Throwable) {
        other.addSuppressed(t)
    }
    return other
}

@Throws(IllegalArgumentException::class, InterruptedException::class)
internal actual inline fun Duration.threadSleep() {
    if (isNegative()) throw IllegalArgumentException("duration cannot be negative")

    var remaining: Long = this.inWholeMicroseconds
    if (remaining < 999_999L) {
        if (usleep(remaining.toUInt()) == -1) {
            // EINTR
            throw InterruptedException()
        }
    } else {
        // Use TimeMark to mitigate slippage for
        // durations greater than 1 second.
        val mark = TimeSource.Monotonic.markNow()
        while (remaining > 0L) {
            val requested = remaining.coerceAtMost(999_999L)
            if (usleep(requested.toUInt()) == -1) {
                // EINTR
                throw InterruptedException()
            }
            val before = remaining
            remaining = (this - mark.elapsedNow()).inWholeMicroseconds
            val slippage = before - remaining - requested
            if (slippage > 0) remaining -= slippage
        }
    }
}

internal actual inline fun Process.hasStdoutStarted(): Boolean = (this as NativeProcess)._hasStdoutStarted
internal actual inline fun Process.hasStderrStarted(): Boolean = (this as NativeProcess)._hasStderrStarted

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
@OptIn(ExperimentalContracts::class, ExperimentalForeignApi::class)
internal inline fun Int.check(
    condition: (result: Int) -> Boolean = { it >= 0 },
): Int {
    contract {
        callsInPlace(condition, InvocationKind.EXACTLY_ONCE)
    }

    if (condition(this)) return this
    throw errnoToIOException(errno)
}
