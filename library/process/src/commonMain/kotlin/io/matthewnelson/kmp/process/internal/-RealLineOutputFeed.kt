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
@file:Suppress("RedundantVisibilityModifier")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.process.ReadBuffer
import kotlin.jvm.JvmSynthetic

internal class RealLineOutputFeed internal constructor(dispatch: (line: String?) -> Unit): ReadBuffer.LineOutputFeed() {

    private var _dispatch = dispatch
    private var skipLF: Boolean = false
    private val sb = StringBuilder(2 * 1024)
    private val feed = UTF8.newEncoderFeed(sb::append)
    private var sbMaxLen = 0

    @Throws(IllegalStateException::class)
    public override fun onData(buf: ReadBuffer, len: Int) {
        if (feed.isClosed()) throw IllegalStateException("LineOutputFeed.isClosed[true]")
        buf.capacity().let { capacity ->
            if (capacity <= 0) return
            capacity.checkBounds(0, len)
        }

        for (i in 0 until len) {
            val b = buf[i]

            if (skipLF) {
                skipLF = false
                if (b == LF) continue
            }

            when(b) {
                CR -> skipLF = true
                LF -> {}
                else -> {
                    feed.consume(b)
                    continue
                }
            }

            feed.flush()
            if (sb.length > sbMaxLen) sbMaxLen = sb.length
            try {
                _dispatch(sb.toString())
            } catch (t: Throwable) {
                dereference()
                throw t
            }
            sb.setLength(0)
        }
    }

    public override fun close() {
        val dispatch = _dispatch
        _dispatch = NoOp
        if (feed.isClosed()) return
        var threw: Throwable? = null
        try {
            feed.doFinal()
            if (sb.isNotEmpty()) {
                if (sb.length > sbMaxLen) sbMaxLen = sb.length
                dispatch(sb.toString())
            }
        } catch (t: Throwable) {
            threw = t
            throw t
        } finally {
            dereference()
            try {
                dispatch(null)
            } catch (t: Throwable) {
                threw?.addSuppressed(t) ?: run { throw t }
            }
        }

        return
    }

    @JvmSynthetic
    internal fun dereference() {
        feed.close()
        _dispatch = NoOp
        sb.wipe(len = sbMaxLen)
        sbMaxLen = 0
    }

    private companion object {
        private const val CR: Byte = '\r'.code.toByte()
        private const val LF: Byte = '\n'.code.toByte()

        private val NoOp: (line: String?) -> Unit = {}
    }
}
