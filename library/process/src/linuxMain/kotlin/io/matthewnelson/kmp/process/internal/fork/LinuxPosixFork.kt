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

package io.matthewnelson.kmp.process.internal.fork

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.absolutePath
import io.matthewnelson.kmp.process.internal.PlatformBuilder
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi

@Suppress("NOTHING_TO_INLINE")
@Throws(UnsupportedOperationException::class)
internal actual inline fun PlatformBuilder.fork(): Int = platform.posix.fork()

@Suppress("NOTHING_TO_INLINE")
@Throws(UnsupportedOperationException::class)
internal actual inline fun PlatformBuilder.dup2(
    fd: Int,
    newFd: Int,
): Int = platform.posix.dup2(fd, newFd)

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun PlatformBuilder.execve(
    program: File,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = platform.posix.execve(program.absolutePath, argv, envp)
