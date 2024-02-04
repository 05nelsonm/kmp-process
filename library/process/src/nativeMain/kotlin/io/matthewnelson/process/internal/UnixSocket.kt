package io.matthewnelson.process.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*
import kotlin.concurrent.Volatile

// TODO: io pipes Issue #2
@OptIn(ExperimentalStdlibApi::class)
internal class UnixSocket private constructor(val fd: Int): AutoCloseable {

    @Volatile
    internal var isClosed: Boolean = fd < 0
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        shutdown(fd, SHUT_RDWR)
        close(fd)
    }

    internal companion object {

        @OptIn(ExperimentalForeignApi::class)
        internal fun new(): UnixSocket {
            var descriptor = socket(AF_UNIX, SOCK_STREAM, 0)
            if (descriptor < 0) {
                val errno = errno
                val message = strerror(errno)?.toKString() ?: "errno: $errno"
                println("UNIX_SOCKET[$errno]: $message")
            } else {
                val result = fcntl(descriptor, F_SETFL, O_NONBLOCK)
                if (result != 0) {
                    close(descriptor)
                    descriptor = -1
                    val errno = errno
                    val message = strerror(errno)?.toKString() ?: "errno: $errno"
                    println("FCNTL[$errno]: $message")
                }
            }

            return UnixSocket(descriptor)
        }
    }
}
