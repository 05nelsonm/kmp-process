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
package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.process.ReadBuffer
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

internal class OutputFeedBuffer private constructor(maxSize: Int): OutputFeed.Raw {

    private val maxSize = maxSize.coerceAtLeast(1)
    private val _buffered = ArrayList<ReadBuffer>(10)

    @Volatile
    @get:JvmName("size")
    internal var size = 0
        private set

    @Volatile
    @get:JvmName("hasEnded")
    internal var hasEnded: Boolean = false
        private set

    @Volatile
    @get:JvmName("maxSizeExceeded")
    internal var maxSizeExceeded: Boolean = false
        private set

    internal fun onData(buf: ReadBuffer?, len: Int) {
        if (buf == null) {
            hasEnded = true
            return
        }
        val copyLen = if ((size + len) > maxSize) maxSize - size else len
        if (copyLen <= 0) return
        _buffered.add(buf.copy(copyLen))
        size += copyLen
        if (copyLen != len) maxSizeExceeded = true
    }

    internal fun doFinal(): Output.Buffered {
        val ret = if (size <= 0) EMPTY_OUTPUT else object : Output.Buffered(size) {

            private val buffered = _buffered.toTypedArray()

            override fun get(index: Int): Byte {
                var i = index
                buffered.forEach { buf ->
                    val size = buf.capacity()
                    if (i < size) return buf[i]
                    i -= size
                }
                throw IndexOutOfBoundsException("index[$index] >= length[$length]")
            }

            override fun iterator(): ByteIterator = object : ByteIterator() {

                private var i = 0
                private var iBuf = 0
                private var _buf: ReadBuffer? = buffered[iBuf]

                override fun hasNext(): Boolean = _buf != null

                override fun nextByte(): Byte {
                    val buf = _buf ?: throw NoSuchElementException("Index $length out of bounds for length $length")
                    val b = buf[i++]
                    if (i == buf.capacity()) {
                        _buf = buffered.elementAtOrNull(++iBuf)
                        i = 0
                    }
                    return b
                }
            }

            override fun utf8(): String = _utf8

            private val _utf8: String by lazy {
                val sb = run {
                    var capacity = UTF8.config.decodeOutMaxSize(length.toLong())
                    if (capacity > Int.MAX_VALUE) capacity = Int.MAX_VALUE.toLong()
                    StringBuilder(capacity.toInt())
                }

                @OptIn(InternalProcessApi::class)
                val feed = ReadBuffer.lineOutputFeed { line ->
                    if (line == null) return@lineOutputFeed

                    if ((sb.length + 1 + line.length) > Int.MAX_VALUE) {
                        // Truncated UTF-8 text
                        return@lineOutputFeed
                    }

                    if (sb.isNotEmpty()) sb.appendLine()
                    sb.append(line)
                }

                buffered.forEach { buf -> feed.onData(buf, buf.capacity()) }
                feed.close()
                val s = sb.toString()
                sb.wipe()
                s
            }
        }

        _buffered.clear()
        size = 0
        hasEnded = false
        maxSizeExceeded = false

        return ret
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(maxSize: Int) = OutputFeedBuffer(maxSize)

        @JvmSynthetic
        internal fun of(options: Output.Options) = OutputFeedBuffer(options.maxBuffer)

        @get:JvmSynthetic
        internal val EMPTY_OUTPUT = object : Output.Buffered(length = 0) {
            private val _iterator = ByteArray(0).iterator()
            override fun get(index: Int): Byte = throw IndexOutOfBoundsException("length == 0")
            override fun iterator(): ByteIterator = _iterator
            override fun utf8(): String = ""
        }
    }

    override fun onOutput(len: Int, get: ((index: Int) -> Byte)?) = error("Use OutputFeedBuffer.onData")
}
