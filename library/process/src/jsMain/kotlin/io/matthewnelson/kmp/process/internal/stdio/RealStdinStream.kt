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
package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.process.StdinStream
import io.matthewnelson.kmp.process.internal.checkBounds
import io.matthewnelson.kmp.process.internal.stream_Writable
import io.matthewnelson.kmp.process.internal.toUInt8Array

// TODO: Issue #77
internal class RealStdinStream internal constructor(
    private val stream: stream_Writable,
): StdinStream() {

    private var isClosed: Boolean = try {
        // v18.0.0
        stream.asDynamic().closed as Boolean
    } catch (_: Throwable) {
        stream.destroyed
    }

    // @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    override fun write(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (isClosed) throw IOException("StdinStream is closed")
        if (len == 0) return

        val chunk = buf.toUInt8Array(offset, len, checkBounds = false)

        println("WRITE_START[${chunk.hashCode()}]")

        try {
            if (stream.write(chunk)) return
            println("DRAIN_ATTACH[${chunk.hashCode()}]")
            stream.once("drain") {
                println("DRAIN[${chunk.hashCode()}]")
                try {
                    stream.write(chunk)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            throw t.toIOException()
        }

        println("WRITE_END[${chunk.hashCode()}]")
    }

    // @Throws(IOException::class)
    override fun close() {
        if (isClosed) return
        if (stream.destroyed) return
        stream.destroy()
    }

    init {
        if (!isClosed) {
            stream.once("close") {
                println("CLOSED")
                isClosed = true
            }
        }
    }
}
