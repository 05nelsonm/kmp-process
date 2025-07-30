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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class SynchronizedSet<E: Any?> internal constructor(initialCapacity: Int = 1) {
    @DoNotReferenceDirectly("SynchronizedSet.withLock")
    internal val lock = newLock()
    @DoNotReferenceDirectly("SynchronizedSet.withLock")
    internal val set = LinkedHashSet<E>(initialCapacity.coerceAtLeast(1), 1.0F)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T: Any?, E: Any?> SynchronizedSet<E>.withLock(
    block: LinkedHashSet<E>.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    @OptIn(DoNotReferenceDirectly::class)
    return lock.withLock { block(set) }
}
