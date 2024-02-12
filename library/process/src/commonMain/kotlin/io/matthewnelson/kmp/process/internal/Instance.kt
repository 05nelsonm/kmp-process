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
package io.matthewnelson.kmp.process.internal

internal class Instance<T: Any?> internal constructor(create: () -> T) {

    private var create: (() -> T)? = create
    private val instance = SynchronizedSet<T>()

    internal fun getOrCreate(): T = instance.withLock {
        if (isEmpty()) {
            create?.let { function ->
                function().also { element ->
                    create = null
                    add(element)
                }
            } ?: throw IllegalStateException()
        } else {
            first()
        }
    }

    internal fun getOrNull(): T? = instance.withLock { firstOrNull() }
}
