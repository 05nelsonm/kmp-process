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
package io.matthewnelson.kmp.process.internal.spawn

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import platform.posix.sigemptyset
import platform.posix.sigset_t

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun MemScope.sigsetInitEmpty(action: (CValuesRef<*>) -> Int): Int {
    val sigset = alloc<sigset_t>()
    if (sigemptyset(sigset.ptr) == -1) return -1
    return action(sigset.ptr)
}
