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
@file:Suppress("NOTHING_TO_INLINE", "VariableInitializerIsRedundant")

package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.NativeCloseable
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.tryCloseSuppressed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalForeignApi::class)
internal class StdioDescriptor private constructor(
    fd: Int,
    internal val canRead: Boolean,
    internal val canWrite: Boolean,
): NativeCloseable {

    init { if (fd < 0) throw AssertionError("fd[$fd] < 0") }

    private val _fd: AtomicReference<Int?> = AtomicReference(fd)
    @property:DoNotReferenceDirectly(useInstead = "withFd")
    internal val fd: Int get() = _fd.value ?: throw IOException("descriptor is closed")
    override val isClosed: Boolean get() = _fd.value == null

    @Throws(IOException::class)
    override fun close() {
        val fd = _fd.getAndSet(null) ?: return

        when (fd) {
            STDIN_FILENO,
            STDOUT_FILENO,
            STDERR_FILENO -> return
        }

        while (true) {
            if (close(fd) == 0) break
            if (errno == EINTR) continue
            throw errnoToIOException(errno)
        }
    }

    internal companion object {

        internal val STDIN get() = StdioDescriptor(STDIN_FILENO, canRead = false, canWrite = true)
        internal val STDOUT get() = StdioDescriptor(STDOUT_FILENO, canRead = true, canWrite = false)
        internal val STDERR get() = StdioDescriptor(STDERR_FILENO, canRead = true, canWrite = false)

        @Throws(IOException::class)
        @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
        internal fun Stdio.File.fdOpen(isStdin: Boolean): StdioDescriptor {
            var mode = 0
            var flags = if (isStdin) O_RDONLY else O_WRONLY

            flags = flags.orOCloExec()

            if (!isStdin && file != STDIO_NULL) {
                // File may need to be created if it does not currently exist
                mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH
                flags = flags or O_CREAT or (if (append) O_APPEND else O_TRUNC)
            }

            var fd = -1
            while (true) {
                fd = open(file.path, flags, mode)
                if (fd != -1) break
                if (errno == EINTR) continue
                throw errnoToIOException(errno, file)
            }

            val descriptor = StdioDescriptor(fd, canRead = isStdin, canWrite = !isStdin)

            if (isStdin) {
                // Need to verify that the file being opened using O_RDONLY is not a directory,
                // otherwise ReadStream.read will fail.
                memScoped {
                    val stat = alloc<stat>()
                    if (fstat(fd, stat.ptr) == -1) {
                        val e = errnoToIOException(errno)
                        throw descriptor.tryCloseSuppressed(e)
                    }
                    val isDirectory = (stat.st_mode.toInt() and S_IFMT) == S_IFDIR
                    if (!isDirectory) return@memScoped

                    val e = errnoToIOException(EISDIR, file)
                    throw descriptor.tryCloseSuppressed(e)
                }
            }

            return descriptor
        }
    }

    internal class Pipe private constructor(
        internal val isPipe1: Boolean,
        fd0: Int,
        fd1: Int,
    ): NativeCloseable {

        internal val read = StdioDescriptor(fd0, canRead = true, canWrite = false)
        internal val write = StdioDescriptor(fd1, canRead = false, canWrite = true)

        override val isClosed: Boolean get() = read.isClosed && write.isClosed

        @Throws(IOException::class)
        override fun close() {
            var threw: IOException? = null

            try {
                read.close()
            } catch (e: IOException) {
                threw = e
            }
            try {
                write.close()
            } catch (e: IOException) {
                if (threw != null) e.addSuppressed(threw)
                threw = e
            }

            if (threw != null) throw threw
        }

        internal companion object {

            @Suppress("UnusedReceiverParameter")
            @Throws(IOException::class)
            internal fun Stdio.Pipe.fdOpen(readEndNonBlock: Boolean = false): Pipe = ::Pipe.fdOpen(readEndNonBlock)
        }
    }
}

internal expect inline fun Int.orOCloExec(): Int

@Throws(IOException::class)
internal expect fun ((isPipe1: Boolean, fd0: Int, fd1: Int) -> StdioDescriptor.Pipe).fdOpen(
    readEndNonBlock: Boolean
): StdioDescriptor.Pipe

/**
 * Performs retries (minimum of 3) for [action] in the event [action] returns
 * -1 and [errno] is [EINTR] (that action was interrupted). Returns the result
 * of [action].
 * */
@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun StdioDescriptor.withFd(retries: Int = 3, action: (fd: Int) -> Int): Int {
    contract {
        callsInPlace(action, InvocationKind.UNKNOWN)
    }

    var limit = retries.coerceAtLeast(3)
    var ret = -1
    while (limit-- > 0) {
        @OptIn(DoNotReferenceDirectly::class)
        ret = action(fd)
        if (ret == -1 && errno == EINTR) continue
        break
    }
    return ret
}
