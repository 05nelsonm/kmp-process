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

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * TODO
 * */
public expect class AsyncWriteStream: Closeable {

    /**
     * TODO
     *
     * @throws [IOException]
     * @throws [IndexOutOfBoundsException]
     * */
    @Throws(CancellationException::class, IOException::class)
    public suspend fun writeAsync(buf: ByteArray, offset: Int, len: Int)

    /**
     * TODO
     * */
    @Throws(CancellationException::class, IOException::class)
    public suspend fun writeAsync(buf: ByteArray)

    /**
     * TODO
     * */
    @Throws(CancellationException::class, IOException::class)
    public suspend fun flushAsync()

    /**
     * TODO
     * */
    @Throws(CancellationException::class, IOException::class)
    public suspend fun closeAsync()

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public fun flush()

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public override fun close()
}
