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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import kotlin.concurrent.AtomicReference

internal actual class SynchronizedSet<E: Any?> internal actual constructor() {

    private val set = LinkedHashSet<E>(1, 1.0F)
    private val lock = AtomicReference<Int?>(null)

    internal actual fun <T: Any?> withLock(
        block: MutableSet<E>.() -> T
    ): T {
        val hc = Any().hashCode()

        val result = try {
            while (true) {
                if (lock.compareAndSet(null, hc)) {
                    break
                }
            }

            block(set)
        } finally {
            lock.value = null
        }

        return result
    }
}
