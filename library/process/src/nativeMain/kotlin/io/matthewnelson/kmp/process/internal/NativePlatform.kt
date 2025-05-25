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

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.*
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

@Suppress("NOTHING_TO_INLINE")
@Throws(InterruptedException::class)
internal actual inline fun Duration.threadSleep() {
    if (usleep(inWholeMicroseconds.toUInt()) == -1) {
        throw when (errno) {
            EINVAL -> IllegalArgumentException()
            else -> InterruptedException()
        }
    }
}

@Throws(IOException::class)
internal fun String.toProgramPaths(): Set<String> {
    val file = toFile()

    if (file.isAbsolute()) {
        return immutableSetOf(file.normalize().path)
    }

    // Relative path
    if (file.path.contains(SysDirSep)) {
        return immutableSetOf(file.absoluteFile.normalize().path)
    }

    // Try finding via PATH
    @OptIn(ExperimentalForeignApi::class)
    val paths = getenv("PATH")
        ?.toKString()
        ?.split(if (IsWindows) ';' else ':')
        ?.iterator()
        ?: throw IOException("PATH environment variable not found. Unable to locate program[$this]")

    val programPaths = ArrayList<String>(1)

    while (paths.hasNext()) {
        val target = paths.next().toFile().resolve(file)

        if (target.isProgramOrNull() != true) continue
        programPaths.add(target.path)
    }

    if (programPaths.isEmpty()) {
        throw IOException("Failed to locate program[$this]")
    }

    return programPaths.toImmutableSet()
}

internal expect fun File.isProgramOrNull(): Boolean?

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
