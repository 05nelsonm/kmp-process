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
import io.matthewnelson.kmp.process.internal.checkBounds
import io.matthewnelson.kmp.process.internal.newLock
import io.matthewnelson.kmp.process.internal.withLock
import kotlin.concurrent.Volatile

public actual sealed class BufferedWriteStream actual constructor(
    private val stream: WriteStream
) {

    private val buf = ByteArray(1024 * 8)
    @Volatile
    private var bufLen = 0
    private val lock = newLock()

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    public actual fun write(buf: ByteArray, offset: Int, len: Int) { lock.withLock { writeNoLock(buf, offset, len) } }

    @Throws(IOException::class)
    public actual fun write(buf: ByteArray) { write(buf, 0, buf.size) }

    @Throws(IOException::class)
    public actual fun close() {
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
    public actual fun flush() { lock.withLock { flushNoLock() } }

    @Throws(IOException::class)
    private fun writeNoLock(buf: ByteArray, offset: Int, len: Int) {
        if (len >= this.buf.size) {
            flushNoLock()
            stream.write(buf, offset, len)
        } else {
            buf.checkBounds(offset, len)

            if (len > (this.buf.size - bufLen)) {
                flushNoLock()
            }

            buf.copyInto(this.buf, bufLen, offset, len + offset)
            bufLen += len
        }
    }

    @Throws(IOException::class)
    private fun flushNoLock() {
        if (bufLen > 0) {
            stream.write(buf, 0, bufLen)
            bufLen = 0
        }
    }
}
