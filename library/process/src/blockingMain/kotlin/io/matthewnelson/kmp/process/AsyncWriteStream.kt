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
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.process.internal.WriteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

/**
 * A stream for writing data asynchronously. All `*Async` function calls
 * utilize [Dispatchers.IO] + [NonCancellable] context and simply call
 * [BufferedWriteStream] blocking APIs.
 *
 * @see [Process.input]
 * @see [BufferedWriteStream]
 * */
public actual class AsyncWriteStream private constructor(stream: WriteStream): BufferedWriteStream(stream), Closeable {

    // Process.Builder.createOutputAsync implementation wraps calls using
    // withContext. This de-duplicates things.
    @Volatile
    private var _isAsyncOutput: Boolean = false

    /**
     * Writes [len] number of bytes from [buf], starting at index [offset].
     *
     * @param [buf] The array of data to write.
     * @param [offset] The index in [buf] to start at when writing data.
     * @param [len] The number of bytes from [buf], starting at index [offset], to write.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * @throws [IndexOutOfBoundsException] If [offset] or [len] are inappropriate.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun writeAsync(buf: ByteArray, offset: Int, len: Int) {
        if (_isAsyncOutput) {
            write(buf, offset, len)
        } else {
            withContext(NonCancellable + Dispatchers.IO) { write(buf, offset, len) }
        }
    }

    /**
     * Writes the entire contents of [buf].
     *
     * @param [buf] the array of data to write.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun writeAsync(buf: ByteArray) {
        if (_isAsyncOutput) {
            write(buf)
        } else {
            withContext(NonCancellable + Dispatchers.IO) { write(buf) }
        }
    }

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun flushAsync() {
        if (_isAsyncOutput) {
            flush()
        } else {
            withContext(NonCancellable + Dispatchers.IO) { flush() }
        }
    }

    /**
     * Closes the resource releasing any system resources that may
     * be allocated to this [AsyncWriteStream]. Subsequent invocations
     * do nothing.
     *
     * @see [use]
     *
     * @throws [IOException] If an I/O error occurs.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun closeAsync() {
        withContext(NonCancellable + Dispatchers.IO) { close() }
    }

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public actual override fun flush() {
        super.flush()
    }

    /**
     * Closes the resource releasing any system resources that may
     * be allocated to this [AsyncWriteStream]. Subsequent invocations
     * do nothing.
     *
     * @see [use]
     *
     * @throws [IOException] If an I/O error occurs.
     * */
    @Throws(IOException::class)
    public actual override fun close() {
        super.close()
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(stream: WriteStream) = AsyncWriteStream(stream)
    }

    @JvmSynthetic
    internal fun configureForAsyncOutput() {
        _isAsyncOutput = true
    }
}
