package io.matthewnelson.process.internal

import io.matthewnelson.process.ProcessException
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

        @Throws(ProcessException::class)
        internal fun new(): UnixSocket {
            val descriptor = socket(AF_UNIX, SOCK_STREAM, 0).check()

            if (fcntl(descriptor, F_SETFL, O_NONBLOCK) != 0) {
                close(descriptor)
                throw errnoToProcessException(errno)
            }

            return UnixSocket(descriptor)
        }
    }
}
