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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE", "FunctionName")

package io.matthewnelson.kmp.process.internal.spawn

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr

@OptIn(ExperimentalForeignApi::class)
internal inline fun MemScope.posix_spawnattr_init(): CValuesRef<posix_spawnattr_tVar>? {
    val attrs = alloc<posix_spawnattr_tVar>()
    if (posix_spawnattr_init(attrs.ptr) != 0) {
        return null
    }
    defer { posix_spawnattr_destroy(attrs.ptr) }
    return attrs.ptr
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun MemScope.posix_spawn_file_actions_init(): CValuesRef<posix_spawn_file_actions_tVar>? {
    val fileActions = alloc<posix_spawn_file_actions_tVar>()
    if (posix_spawn_file_actions_init(fileActions.ptr) != 0) {
        return null
    }
    defer { posix_spawn_file_actions_destroy(fileActions.ptr) }
    return fileActions.ptr
}
