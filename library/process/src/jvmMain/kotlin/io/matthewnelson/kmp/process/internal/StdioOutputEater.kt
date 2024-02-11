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

import io.matthewnelson.kmp.file.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile

internal class StdioOutputEater private constructor(
    private val maxBuffer: Int,
    stdout: InputStream,
    stderr: InputStream,
): AutoCloseable {

    @Volatile
    internal var maxBufferExceeded: Boolean = false
        private set

    private val executors = Executors.newFixedThreadPool(2) {
        Thread(it).apply { isDaemon = true }
    }

    private val stdoutBytes = ByteBufferOutputStream()
    private val stderrBytes = ByteBufferOutputStream()

    init {
        executors.execute(Eater(stdout, stdoutBytes))
        executors.execute(Eater(stderr, stderrBytes))
    }

    internal fun doFinal(): Pair<String, String> {
        executors.shutdown()
        return stdoutBytes.toString() to stderrBytes.toString()
    }

    override fun close() {
        executors.shutdown()
        stdoutBytes.close()
        stderrBytes.close()
    }

    private inner class Eater(
        private val output: InputStream,
        private val buffer: ByteBufferOutputStream,
    ): Runnable {
        override fun run() {
            val buf = ByteArray(4096)

            while (!executors.isShutdown) {
                var read = try {
                    output.read(buf)
                } catch (_: IOException) {
                    // stream closed
                    break
                }

                if (read == -1) break

                val remaining = maxBuffer - buffer.size()
                var exceeded = false

                if (read > remaining) {
                    exceeded = true
                    read = if (remaining < 1) 0 else remaining
                }

                buffer.write(buf, 0, read)

                if (exceeded) {
                    maxBufferExceeded = true
                    break
                }
            }

            buf.fill(0)
        }
    }

    private class ByteBufferOutputStream: ByteArrayOutputStream(8192) {
        override fun write(b: ByteArray, off: Int, len: Int) {
            val oldBuf = buf
            super.write(b, off, len)
            if (oldBuf.hashCode() == buf.hashCode()) return

            // clear old buffer before de-referencing if it was re-sized
            oldBuf.fill(0)
        }

        override fun close() {
            buf.fill(0, toIndex = size())
            reset()
            super.close()
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            maxBuffer: Int,
            stdout: InputStream,
            stderr: InputStream,
        ) = StdioOutputEater(maxBuffer, stdout, stderr)
    }
}
