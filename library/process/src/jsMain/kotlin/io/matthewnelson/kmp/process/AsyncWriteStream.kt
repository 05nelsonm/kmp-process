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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.process.internal.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/**
 * A stream to write to.
 * */
public actual class AsyncWriteStream internal constructor(
    private val stream: stream_Writable,
) {

    private val isClosed: Boolean get() = !stream.writable

    // @Throws(
    //     CancellationException::class,
    //     IllegalArgumentException::class,
    //     IndexOutOfBoundsException::class,
    //     IOException::class,
    // )
    public actual suspend fun writeAsync(buf: ByteArray, offset: Int, len: Int) {
        buf.checkBounds(offset, len)
        if (isClosed) throw IOException("WriteStream is closed")
        if (len == 0) return

        val chunk = buf.toJsArray(offset, len) { size -> Uint8Array(size) }
        val wLatch: CompletableJob = Job(currentCoroutineContext()[Job])
        var dLatch: CompletableJob? = null

        try {
            if (!stream.write(chunk) { wLatch.complete(); chunk.fill() }) {
                dLatch = Job(wLatch)
                stream.once("drain") { dLatch.complete() }
            }
        } catch (t: Throwable) {
            wLatch.cancel()
            dLatch?.cancel()
            throw t.toIOException()
        }

        dLatch?.join()
        wLatch.join()
    }

    // @Throws(CancellationException::class, IOException::class)
    public actual suspend fun writeAsync(buf: ByteArray) { writeAsync(buf, 0, buf.size) }

    // @Throws(CancellationException::class, IOException::class)
    public actual suspend fun flushAsync() { flush() }

    // @Throws(CancellationException::class, IOException::class)
    public actual suspend fun closeAsync() { close() }

    // @Throws(IOException::class)
    public actual fun flush() {}

    // @Throws(IOException::class)
    public actual fun close() { stream.end() }
}
