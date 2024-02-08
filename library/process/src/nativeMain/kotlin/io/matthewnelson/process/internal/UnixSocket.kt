package io.matthewnelson.process.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*
import kotlin.concurrent.Volatile

// TODO: io pipes Issue #2
@OptIn(ExperimentalStdlibApi::class)
internal class UnixSocket private constructor(val fd: Int): AutoCloseable {

    @Volatile
    internal var isClosed: Boolean = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        shutdown(fd, SHUT_RDWR)
        close(fd)
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun new(): UnixSocket {
            val descriptor = socket(AF_UNIX, SOCK_STREAM, 0).check()

            if (fcntl(descriptor, F_SETFL, O_NONBLOCK) != 0) {
                close(descriptor)
                @OptIn(DelicateFileApi::class, ExperimentalForeignApi::class)
                throw errnoToIOException(errno)
            }

            return UnixSocket(descriptor)
        }
    }
}
