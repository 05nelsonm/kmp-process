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
package io.matthewnelson.process.internal

import io.matthewnelson.process.ProcessException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalForeignApi::class)
internal fun errnoToProcessException(errno: Int): ProcessException {
    val message = strerror(errno)?.toKString() ?: "errno: $errno"
    return ProcessException(message)
}

@Throws(ProcessException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun Int.check(
    block: (result: Int) -> Boolean = { it >= 0 },
): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    if (!block(this)) {
        throw errnoToProcessException(errno)
    }

    return this
}
