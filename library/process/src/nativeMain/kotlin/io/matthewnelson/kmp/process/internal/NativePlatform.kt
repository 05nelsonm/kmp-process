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

import io.matthewnelson.kmp.file.*
import kotlinx.cinterop.*
import platform.posix.errno
import platform.posix.getenv
import platform.posix.usleep
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

@Throws(IOException::class)
internal fun String.toProgramFile(): File {
    val file = toFile()
    var absolute: File? = null

    if (file.isAbsolute()) {
        absolute = file
    }

    // Relative path
    if (absolute == null && file.path.contains(SysDirSep)) {
        absolute = file.absoluteFile
    }

    // Try finding via PATH
    if (absolute == null) {
        @OptIn(ExperimentalForeignApi::class)
        val paths = getenv("PATH")
            ?.toKString()
            ?.split(if (IsWindows) ';' else ':')
            ?.iterator()
            ?: throw IOException("PATH environment variable not found. Unable to locate program[$this]")

        while (absolute == null && paths.hasNext()) {
            val target = paths.next().toFile().resolve(file)

            // TODO: Check stats for regular file
            //  and can execute (Unix Only)
            //  or go to next
            //  >> https://github.com/05nelsonm/kmp-file/issues/55
            if (target.exists()) {
                absolute = target
            }
        }
    }

    return absolute?.normalize() ?: throw FileNotFoundException("Failed to locate program[$this]")
}

@OptIn(ExperimentalForeignApi::class)
internal fun List<String>.toArgv(
    program: File,
    scope: MemScope,
): CArrayPointer<CPointerVar<ByteVar>> = with(scope) {
    val argv = allocArray<CPointerVar<ByteVar>>(size + 2)

    argv[0] = program.name.cstr.ptr

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
        val (k, v) = iterator.next()
        envp[i++] = "$k=$v".cstr.ptr
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
        @OptIn(ExperimentalForeignApi::class)
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
