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

import io.matthewnelson.kmp.file.IOException

internal interface Closeable {
    val isClosed: Boolean

    @Throws(IOException::class)
    fun close()
}

internal inline fun <T: Throwable> Closeable.tryCloseSuppressed(throwable: T): T {
    try {
        close()
    } catch (e: IOException) {
        throwable.addSuppressed(e)
    }
    return throwable
}
