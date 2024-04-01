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

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.WriteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

/**
 * A stream to write to. On Jvm & Native, blocking APIs are
 * available via [BufferedWriteStream].
 *
 * On Jvm & Native, all Async functions utilize Dispatchers.IO
 * under the hood when calling BufferedWriteStream functions.
 *
 * **NOTE:** For Jvm & Android the `kotlinx.coroutines.core`
 * dependency is needed when using Async functions.
 * */
public actual class AsyncWriteStream private constructor(
    stream: WriteStream,
): BufferedWriteStream(stream) {

    @Throws(
        CancellationException::class,
        IllegalArgumentException::class,
        IndexOutOfBoundsException::class,
        IOException::class,
    )
    public actual suspend fun writeAsync(buf: ByteArray, offset: Int, len: Int) {
        withContext(NonCancellable + Dispatchers.IO) { write(buf, offset, len) }
    }

    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun writeAsync(buf: ByteArray) {
        withContext(NonCancellable + Dispatchers.IO) { write(buf) }
    }

    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun flushAsync() {
        withContext(NonCancellable + Dispatchers.IO) { flush() }
    }

    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun closeAsync() {
        withContext(NonCancellable + Dispatchers.IO) { close() }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(stream: WriteStream) = AsyncWriteStream(stream)
    }
}
