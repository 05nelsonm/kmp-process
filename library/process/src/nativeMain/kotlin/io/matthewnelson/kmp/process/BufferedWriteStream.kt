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
import io.matthewnelson.kmp.process.internal.checkBounds
import io.matthewnelson.kmp.process.internal.newLock
import io.matthewnelson.kmp.process.internal.withLock
import kotlin.concurrent.Volatile

/**
 * A stream for writing data synchronously, buffering any writes until 8192 bytes
 * are accumulated.
 *
 * @see [AsyncWriteStream]
 * */
public actual sealed class BufferedWriteStream actual constructor(private val stream: WriteStream): Closeable {

    @Volatile
    private var _bufLen = 0
    private val buf = ByteArray(1024 * 8)
    private val lock = newLock()

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
    @Throws(IOException::class)
    public actual fun write(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (len == 0) return
        lock.withLock { writeNoLock(buf, offset, len) }
    }

    /**
     * Writes the entire contents of [buf].
     *
     * @param [buf] the array of data to write.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public actual fun write(buf: ByteArray) { write(buf, 0, buf.size) }

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public actual open fun flush() { lock.withLock { flushNoLock() } }

    /**
     * Closes the resource releasing any system resources that may
     * be allocated to this [BufferedWriteStream]. Subsequent invocations
     * do nothing.
     *
     * Any buffered data is written to the underlying stream via [flush]
     * prior to closing.
     *
     * @see [use]
     *
     * @throws [IOException] If an I/O error occurs.
     * */
    @Throws(IOException::class)
    public actual override fun close() {
        if (stream.isClosed) return

        lock.withLock {
            if (stream.isClosed) return@withLock
            var threw: IOException? = null

            try {
                flushNoLock()
            } catch (e: IOException) {
                threw = e
            }

            try {
                stream.close()
            } catch (e: IOException) {
                if (threw != null) {
                    threw.addSuppressed(e)
                } else {
                    threw = e
                }
            }

            buf.fill(0)

            if (threw != null) throw threw
        }
    }

    @Throws(IOException::class)
    private fun writeNoLock(buf: ByteArray, offset: Int, len: Int) {
        if (len >= this.buf.size) {
            flushNoLock()
            stream.write(buf, offset, len)
        } else {
            if (len > (this.buf.size - _bufLen)) {
                flushNoLock()
            }

            buf.copyInto(this.buf, _bufLen, offset, len + offset)
            _bufLen += len
        }
    }

    @Throws(IOException::class)
    private fun flushNoLock() {
        if (_bufLen > 0) {
            stream.write(buf, 0, _bufLen)
            _bufLen = 0
        }
    }
}
