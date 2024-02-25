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

import io.matthewnelson.kmp.process.internal.PlatformBuilder
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi

@Suppress("NOTHING_TO_INLINE")
@Throws(UnsupportedOperationException::class)
internal expect inline fun PlatformBuilder.fork(): Int

@Suppress("NOTHING_TO_INLINE")
@Throws(UnsupportedOperationException::class)
internal expect inline fun PlatformBuilder.dup2(
    fd: Int,
    newFd: Int,
): Int

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal expect inline fun PlatformBuilder.execve(
    command: String,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int
