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

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import platform.posix.errno
import platform.posix.usleep
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal expect fun MemScope.posixSpawn(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): NativeProcess

@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal expect fun MemScope.forkExec(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): NativeProcess

@Suppress("NOTHING_TO_INLINE")
@Throws(InterruptedException::class)
internal actual inline fun Duration.threadSleep() {
    if (usleep(inWholeMicroseconds.toUInt()) == -1) {
        // EINVAL will never happen b/c duration is
        // max 100 millis. Must be EINTR
        throw InterruptedException()
    }
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun Int.check(
    block: (result: Int) -> Boolean = { it >= 0 },
): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    if (!block(this)) {
        @OptIn(DelicateFileApi::class, ExperimentalForeignApi::class)
        throw errnoToIOException(errno)
    }

    return this
}
