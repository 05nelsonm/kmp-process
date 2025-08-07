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

/**
 * A stream for writing data synchronously, buffering any writes until 8192 bytes
 * are accumulated.
 *
 * @see [AsyncWriteStream]
 * */
public expect sealed class BufferedWriteStream(stream: WriteStream): Closeable {

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
    public fun write(buf: ByteArray, offset: Int, len: Int)

    /**
     * Writes the entire contents of [buf].
     *
     * @param [buf] the array of data to write.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public fun write(buf: ByteArray)

    /**
     * Flushes any buffered data.
     *
     * @throws [IOException] If an I/O error occurs, or the stream is closed.
     * */
    @Throws(IOException::class)
    public open fun flush()

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
    public override fun close()
}
