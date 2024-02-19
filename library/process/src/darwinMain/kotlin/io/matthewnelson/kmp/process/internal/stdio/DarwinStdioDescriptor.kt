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

package io.matthewnelson.kmp.process.internal.stdio

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun CPointer<IntVar>.pipe2(
    flags: Int,
): Int {
    // SYS_pipe2 is currently undefined for darwin SDKs
    // This can be checked for periodically by looking in
    // the respective SDK's usr/include/sys/syscall.h
    // e.g.
    //
    //     $ cat "$(xcrun --sdk iphoneos --show-sdk-path)/usr/include/sys/syscall.h" | grep "pipe2"
    //
    // Until then, -1 (failure) is returned  such that flags can
    // then be set non-atomically via pipe1 & fcntl
    return -1
}
