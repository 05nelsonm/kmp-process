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
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.path
import kotlinx.cinterop.*
import platform.posix.dlsym

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun PosixSpawnFileActions.posix_spawn_file_actions_addchdir(
    chdir: File,
    scope: MemScope,
): Int = with (scope) {
    // IOException thrown here if it is not supported on iOS.
    // spawn.h has it declared as API_UNAVAILABLE, but it seems
    // to work???
    // Nevertheless, we throw IOException to end early b/c falling back
    // to fork & exec is not a thing on iOS.
    ADDCHDIR_NP?.invoke(ref, chdir.path.cstr.ptr)
        ?: throw IOException("posix_spawn_file_actions_addchdir_np is not supported on iOS")
}

@OptIn(ExperimentalForeignApi::class)
private val ADDCHDIR_NP by lazy {
    val ptr = dlsym(null, "posix_spawn_file_actions_addchdir_np")
        ?: return@lazy null

    @Suppress("UNCHECKED_CAST")
    ptr as CPointer<CFunction<(CValuesRef<posix_spawn_file_actions_tVar>, CPointer<ByteVarOf<Byte>>) -> Int>>
}
