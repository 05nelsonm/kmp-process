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
@file:Suppress("FunctionName", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException

@Throws(IOException::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun PosixSpawnFileActions.posix_spawn_file_actions_addchdir(
    directory: File,
): Int = throw IOException("posix_spawn_file_actions_addchdir_np is not supported on iOS")
