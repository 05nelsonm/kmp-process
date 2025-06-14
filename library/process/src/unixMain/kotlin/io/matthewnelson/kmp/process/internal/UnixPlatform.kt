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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.withFd
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pin
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.O_NONBLOCK
import platform.posix.errno
import platform.posix.read
import platform.posix.usleep
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal actual val STDIO_NULL: File = "/dev/null".toFile()

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String>

/**
 * Waits for a newly spawned process until [pipe] closure, or an exit code.
 *
 * The [StdioDescriptor.Pipe.read] descriptor **MUST** be configured with [O_NONBLOCK].
 *
 * @param [buf] array for [platform.posix.read]. Must have size 1 or more
 * @param [pipe] the pipe to monitor for child process failure/success
 * @param [onNonZeroExitCode] a callback for producing an error for the non-zero exit code
 *   which will be thrown after cleaning up
 *
 * @return number of bytes read by [platform.posix.read] into [buf], or -1
 *
 * @throws [IndexOutOfBoundsException] if [buf] is empty
 * @throws [IOException] upon [platform.posix.read] failure with an unexpected [errno], or
 *   if [onNonZeroExitCode] returned a non-null value.
 * */
@Throws(IndexOutOfBoundsException::class, IOException::class)
@OptIn(ExperimentalContracts::class, ExperimentalForeignApi::class, UnsafeNumber::class)
internal inline fun NativeProcess.awaitExecOrFailure(
    buf: ByteArray,
    pipe: StdioDescriptor.Pipe,
    onNonZeroExitCode: (code: Int) -> IOException?,
): Int {
    contract {
        callsInPlace(onNonZeroExitCode, InvocationKind.UNKNOWN)
    }

    if (buf.isEmpty()) {
        val e = IndexOutOfBoundsException("buf.isEmpty() == true")
        destroySuppressed(e)
        pipe.tryCloseSuppressed(e)
        throw e
    }

    val fdRead = try {
        pipe.write.close()
        pipe.read.withFd { it }
    } catch (e: IOException) {
        // If we cannot close the write end of the pipe then
        // read will never pop out with a value of 0 when the
        // child process' exec is successful.
        destroySuppressed(e)
        pipe.tryCloseSuppressed(e)
        throw IOException("CLOEXEC pipe failure", e)
    }

    // If pipe(1) was used to open the descriptors there is a chance that,
    // between the time the descriptors were opened and FD_CLOEXEC was
    // configured for each, there was another fork call elsewhere. This
    // can happen in a multithreaded program.
    //
    // If a descriptor WAS leaked to another process, then the write end of
    // the pipe may remain open until that other process exits. This would
    // cause our read to never "pop out" and return 0, because there's still
    // a write end of the pipe open somewhere.
    //
    // In this scenario, we must use a limit on the number of reads we
    // perform as to not end up in an infinite loop.
    //
    // With pipe(2), this is not the case because O_CLOEXEC is configured
    // atomically. We "should" never experience a descriptor leak as described
    // above.
    var limit = if (pipe.isPipe1) 100 else Int.MAX_VALUE
    val micros = 500u // 100 * 0.5ms = 50ms

    var threw: IOException? = null
    val pinned = buf.pin()
    var ret = -1

    while (limit-- > 0) {
        @Suppress("RemoveRedundantCallsOfConversionMethods")
        ret = read(fdRead, pinned.addressOf(0), buf.size.convert()).toInt()
        if (ret >= 0) break

        when (val e = errno) {
            EINTR, EAGAIN, EWOULDBLOCK -> {
                usleep(micros)
                val code = exitCodeOrNull() ?: continue
                if (code != 0) threw = onNonZeroExitCode(code)
                break
            }
            else -> {
                threw = errnoToIOException(e)
                break
            }
        }
    }

    if (ret == -1 && threw == null) {
        // Attempt 1 more read before closing things down.
        //
        // The fork/execve implementation will always return null
        // for onExitCode because its Child process implementation
        // passes its errno via the pipe before calling _exit
        @Suppress("RemoveRedundantCallsOfConversionMethods")
        ret = read(fdRead, pinned.addressOf(0), buf.size.convert()).toInt()
    }

    pinned.unpin()

    try {
        pipe.close()
    } catch (e: IOException) {
        if (threw != null) {
            threw.addSuppressed(e)
        } else {
            threw = IOException("CLOEXEC pipe failure", e)
        }
    }

    threw?.let { t -> throw destroySuppressed(t) }

    return ret
}
