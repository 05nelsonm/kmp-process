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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import platform.Foundation.NSProcessInfo

internal actual inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String> {
    // NSDictionary<NSString *, NSString *>
    val env = NSProcessInfo.processInfo.environment
    val map = LinkedHashMap<String, String>(env.size, 1.0F)
    for (entry in env.entries) {
        val key = entry.key ?: continue
        val value = entry.value ?: continue
        map[key.toString()] = value.toString()
    }
    return map
}
