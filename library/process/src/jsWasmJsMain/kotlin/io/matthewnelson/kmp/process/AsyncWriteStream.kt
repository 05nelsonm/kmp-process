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
@file:OptIn(DelicateFileApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.process.internal.checkBounds
import io.matthewnelson.kmp.process.internal.node.JsWritable
import io.matthewnelson.kmp.process.internal.node.write
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A stream for writing data asynchronously.
 *
 * @see [Process.input]
 * */
public actual class AsyncWriteStream internal constructor(private val stream: JsWritable): Closeable {

    private val isClosed: Boolean get() = !jsExternTryCatch { stream.writable }

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
        if (isClosed) throw ClosedException()
        buf.checkBounds(offset, len)
        if (len == 0) return

        val wLatch: CompletableJob = Job(currentCoroutineContext()[Job])
        var dLatch: CompletableJob? = null

        try {
            if (!stream.write(buf, offset, len, wLatch)) {
                dLatch = Job(wLatch)
                jsExternTryCatch { stream.once("drain", dLatch::complete) }
            }
        } catch (t: Throwable) {
            wLatch.complete()
            dLatch?.complete()
            throw t.toIOException()
        }

        dLatch?.join()
        wLatch.join()
    }

    /**
     * Writes the entire contents of [buf].
     *
     * @param [buf] the array of data to write.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun writeAsync(buf: ByteArray) { writeAsync(buf, 0, buf.size) }

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(CancellationException::class, IOException::class)
    public actual suspend fun flushAsync() { flush() }

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
    public actual suspend fun closeAsync() { close() }

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public actual fun flush() { if (isClosed) throw ClosedException() }

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
        try {
            jsExternTryCatch(stream::end)
        } catch (t: Throwable) {
            throw t.toIOException()
        }
    }
}
