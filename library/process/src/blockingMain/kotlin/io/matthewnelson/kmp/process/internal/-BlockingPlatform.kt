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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.Decoder.Companion.decodeBuffered
import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.process.*
import kotlin.time.Duration

@Throws(InterruptedException::class)
internal expect inline fun Duration.threadSleep()

@Throws(IOException::class)
internal inline fun PlatformBuilder.blockingOutput(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    options: Output.Options,
    destroy: Signal,
): Output = commonOutput(
    command,
    args,
    chdir,
    env,
    stdio,
    options,
    destroy,
    _spawn = PlatformBuilder::spawn,
    _close = AsyncWriteStream::close,
    _write = AsyncWriteStream::write,
    _decodeBuffered = { utf8, throwOnOverflow, stream ->
        decodeBuffered(utf8, throwOnOverflow, stream::write)
    },
    _sleep = Duration::threadSleep,
    _sleepWithContext = Duration::threadSleep,
    _awaitStop = OutputFeed.Waiter::awaitStop,
    _waitFor = Process::waitFor,
)

internal fun ReadStream.scanLines(
    dispatch: (line: String?) -> Unit,
) { scanLines(DEFAULT_BUFFER_SIZE, dispatch) }

@OptIn(InternalProcessApi::class)
@Throws(IllegalArgumentException::class)
internal fun ReadStream.scanLines(
    bufferSize: Int,
    dispatch: (line: String?) -> Unit,
) {
    use { stream ->
        val buf = ReadBuffer.of(ByteArray(bufferSize))
        val feed = ReadBuffer.lineOutputFeed(dispatch)

        var threw: Throwable? = null
        while (true) {
            val read = try {
                stream.read(buf.buf)
            } catch (_: IOException) {
                break
            }

            // If a pipe has no write ends open (i.e. the
            // child process exited), a zero read is returned,
            // and we can end early (before process destruction).
            if (read <= 0) break

            try {
                feed.onData(buf, read)
            } catch (t: Throwable) {
                threw = t
                break
            }
        }

        buf.buf.fill(0)
        threw?.let { throw it }
        feed.close()
    }
}
