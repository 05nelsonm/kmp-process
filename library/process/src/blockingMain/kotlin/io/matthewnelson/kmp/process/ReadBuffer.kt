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

import io.matthewnelson.kmp.process.internal.RealLineOutputFeed
import kotlin.jvm.JvmInline

/**
 * For internal usage only.
 *
 * Wrapper class for buffering data to utilize in platform specific
 * implementations along with [LineOutputFeed], primarily in order
 * to mitigate unnecessarily copying a `Node.js` Buffer to a ByteArray.
 *
 * @see [LineOutputFeed]
 * */
@JvmInline
public actual value class ReadBuffer private actual constructor(private actual val _buf: Any) {

    /**
     * Public, platform specific access to the underlying array.
     * */
    public val buf: ByteArray get() = _buf as ByteArray

    internal actual fun capacity(): Int = buf.size

    @Throws(IndexOutOfBoundsException::class)
    internal actual operator fun get(
        index: Int,
    ): Byte = buf[index]

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
    internal actual fun decodeToUtf8(
        startIndex: Int,
        endIndex: Int,
    ): String = buf.decodeToString(startIndex, endIndex)

    /**
     * Scans buffered input and dispatches lines, disregarding
     * line breaks CR (`\r`), LF (`\n`), & CRLF (`\r\n`).
     *
     * After reading operations are exhausted, calling [close] will
     * assume any buffered input remaining is terminating and dispatch
     * it as a line, and then dispatch `null` to indicate End Of Stream.
     *
     * This is **NOT** thread safe.
     *
     * @see [lineOutputFeed]
     * */
    public actual abstract class LineOutputFeed internal actual constructor() {

        /**
         * Consumes data from [buf] at index 0 until [len]. Data is parsed into
         * individual lines, dispatching each line to provided [lineOutputFeed]
         * dispatcher callback.
         *
         * @throws [IllegalStateException] If closed.
         * @throws [IndexOutOfBoundsException] If [len] is inappropriate.
         * */
        @Throws(IllegalStateException::class)
        public actual abstract fun onData(buf: ReadBuffer, len: Int)

        /**
         * Closes the [LineOutputFeed].
         * */
        public actual abstract fun close()
    }

    public actual companion object {

        /**
         * Allocates a new buffer with capacity of (8 * 1024) bytes
         * */
        @InternalProcessApi
        public actual fun allocate(): ReadBuffer {
            return ReadBuffer(ByteArray(8 * 1024))
        }

        /**
         * Creates a new [LineOutputFeed]
         *
         * e.g. (Jvm)
         *
         *     val feed = ReadBuffer.lineOutputFeed { line ->
         *         println(line ?: "--EOS--")
         *     }
         *
         *     myInputStream.use { iStream ->
         *         val buf = ReadBuffer.allocate()
         *
         *         try {
         *             while(true) {
         *                 val read = iStream.read(buf.buf)
         *                 if (read == -1) break
         *                 feed.onData(buf, read)
         *             }
         *         } finally {
         *
         *             // done reading, dispatch last line
         *             // (if buffered), and dispatch null
         *             // to indicate EOS
         *             feed.close()
         *         }
         *     }
         *
         * **NOTE:** [dispatch] should not throw exception
         * */
        @InternalProcessApi
        public actual fun lineOutputFeed(
            dispatch: (line: String?) -> Unit,
        ): LineOutputFeed = RealLineOutputFeed(dispatch)

        /**
         * Wraps a [ByteArray] to use as [ReadBuffer].
         *
         * @see [buf]
         * */
        @InternalProcessApi
        public fun of(buf: ByteArray): ReadBuffer = ReadBuffer(buf)
    }
}
