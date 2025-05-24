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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import platform.posix.__environ

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String> {
    val map = LinkedHashMap<String, String>(10, 1.0F)
    val env = __environ ?: return map
    var i = 0
    while (true) {
        val arg = env[i++]?.toKString() ?: break
        val key = arg.substringBefore('=')
        val value = arg.substringAfter('=')
        map[key] = value
    }
    return map
}
