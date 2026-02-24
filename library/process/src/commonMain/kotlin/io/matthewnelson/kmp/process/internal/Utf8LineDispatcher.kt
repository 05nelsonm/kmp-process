/*
 * Copyright (c) 2026 Matthew Nelson
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
@file:Suppress("DuplicatedCode")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.OutputFeed
import kotlin.jvm.JvmSynthetic

// TODO: Maybe upstream to encoding:utf8?
internal class Utf8LineDispatcher private constructor(dispatch: (line: String?) -> Unit): Closeable, OutputFeed.Raw {

    private var _dispatch = dispatch
    private var skipLF: Boolean = false
    private val sb = StringBuilder(DEFAULT_BUFFER_SIZE / 4)
    private val feed = UTF8.newEncoderFeed(sb::append)
    private var sbMaxLen = 0

    internal fun isClosed(): Boolean = feed.isClosed()

    @Throws(EncodingException::class)
    internal fun update(offset: Int, len: Int, get: (i: Int) -> Byte) {
        for (i in offset until len) {
            val b = get(i)

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
                close()
                throw t
            }
            sb.setLength(0)
        }
    }

    @Throws(EncodingException::class)
    internal fun doFinal() {
        val dispatch = _dispatch
        _dispatch = NoOp
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
            close()
            try {
                dispatch(null)
            } catch (t: Throwable) {
                threw?.addSuppressed(t) ?: run { throw t }
            }
        }

        return
    }

    override fun close() {
        feed.close()
        _dispatch = NoOp
        sb.wipe(len = sbMaxLen)
        sbMaxLen = 0
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(dispatch: (line: String?) -> Unit): Utf8LineDispatcher = Utf8LineDispatcher(dispatch)

        private const val CR: Byte = '\r'.code.toByte()
        private const val LF: Byte = '\n'.code.toByte()

        private val NoOp: (line: String?) -> Unit = {}
    }

    override fun onOutput(data: Output.Data?) { error("Use Utf8LineDispatcher.{update/doFinal/close}") }
}