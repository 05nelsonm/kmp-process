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

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.kmp.file.Buffer
import io.matthewnelson.kmp.process.internal.RealLineOutputFeed

/**
 * For internal usage only.
 *
 * Wrapper class for buffering data to utilize in platform specific
 * implementations along with [LineOutputFeed], primarily in order
 * to mitigate unnecessarily copying a `Node.js` Buffer to a ByteArray.
 *
 * @see [LineOutputFeed]
 * @suppress
 * */
public actual value class ReadBuffer private actual constructor(private actual val _buf: Any) {

    /**
     * Public, platform specific access to the underlying [Buffer].
     * */
    public val buf: Buffer get() = (_buf as Buffer)

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
     * e.g. (Using `io.matthewnelson.kmp-file:file`)
     *
     *     val feed = ReadBuffer.lineOutputFeed { line ->
     *         println(line ?: "--EOS--")
     *     }
     *
     *     "/path/to/file.txt".toFile().openRead().use { stream ->
     *         val buf = ReadBuffer.allocate()
     *
     *         try {
     *             while(true) {
     *                 // ReadBuffer.buf available from blockingMain
     *                 // source set (Jvm/Native) as ByteArray
     *                 //
     *                 // ReadBuffer.buf available from jsWasmJsMain
     *                 // source set (Js/WasmJs) as Buffer
     *                 val read = stream.read(buf.buf)
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
     * @see [lineOutputFeed]
     * */
    public actual abstract class LineOutputFeed internal actual constructor() {

        /**
         * Consumes data from [buf] at index 0 until [len]. Data is parsed into
         * individual lines, dispatching each line to provided [lineOutputFeed]
         * dispatcher callback.
         *
         * **NOTE:** If dispatching to [lineOutputFeed] callback results in an
         * exception, the feed is closed and exception re-thrown.
         *
         * @throws [IllegalStateException] If closed.
         * @throws [IndexOutOfBoundsException] If [len] is inappropriate.
         * */
        @Throws(IllegalStateException::class)
        public actual abstract fun onData(buf: ReadBuffer, len: Int)

        /**
         * Closes the [LineOutputFeed]. Any buffered input will be dispatched
         * to the provided [lineOutputFeed] callback, followed by `null` to
         * indicate end of stream. Successive invocations of [close] are ignored.
         * */
        public actual abstract fun close()
    }

    public actual companion object {

        /**
         * Allocates a new buffer with capacity of (8 * 1024) bytes.
         *
         * @throws [UnsupportedOperationException] on Js/WasmJs Browser
         * */
        @InternalProcessApi
        public actual fun allocate(): ReadBuffer {
            return ReadBuffer(Buffer.alloc(DEFAULT_BUFFER_SIZE))
        }

        /**
         * Creates a new [LineOutputFeed].
         *
         * **NOTE:** [dispatch] should not throw exception. If it does, the feed
         * will be closed and the exception re-thrown.
         * */
        @InternalProcessApi
        public actual fun lineOutputFeed(
            dispatch: (line: String?) -> Unit
        ): LineOutputFeed = RealLineOutputFeed(dispatch)

        /**
         * Wraps a [Buffer] to use as [ReadBuffer].
         *
         * @see [ReadBuffer.buf]
         * */
        @InternalProcessApi
        public fun of(buf: Buffer): ReadBuffer = ReadBuffer(buf)
    }

    internal actual fun capacity(): Int = buf.length.toInt()

    @Throws(IndexOutOfBoundsException::class)
    internal actual operator fun get(index: Int): Byte = buf.readInt8(index)

    internal actual fun functionGet(): (index: Int) -> Byte = buf::readInt8
}
