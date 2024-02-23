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
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.*
import platform.posix.errno
import platform.posix.usleep
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

@Throws(IOException::class, UnsupportedOperationException::class)
internal expect fun posixSpawn(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    handle: StdioHandle,
    destroy: Signal,
): NativeProcess

@Throws(IOException::class)
internal expect fun forkExec(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    handle: StdioHandle,
    destroy: Signal,
): NativeProcess

@OptIn(ExperimentalForeignApi::class)
internal fun List<String>.toArgv(
    argv0: String,
    scope: MemScope,
): CArrayPointer<CPointerVar<ByteVar>> = with(scope) {
    val argv = allocArray<CPointerVar<ByteVar>>(size + 2)

    argv[0] = argv0.cstr.ptr

    var i = 1
    val iterator = iterator()
    while (iterator.hasNext()) {
        argv[i++] = iterator.next().cstr.ptr
    }

    argv[i] = null

    argv
}

@OptIn(ExperimentalForeignApi::class)
internal fun Map<String, String>.toEnvp(
    scope: MemScope,
): CArrayPointer<CPointerVar<ByteVar>> = with(scope) {
    val envp = allocArray<CPointerVar<ByteVar>>(size + 1)

    var i = 0
    val iterator = entries.iterator()
    while (iterator.hasNext()) {
        envp[i++] = iterator.next().toString().cstr.ptr
    }

    envp[i] = null

    envp
}

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

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun ByteArray.checkBounds(offset: Int, len: Int) {
    if (size - offset < len) throw IllegalArgumentException("Input too short")
    if (offset < 0 || len < 0 || offset > size - len) throw IndexOutOfBoundsException()
}
