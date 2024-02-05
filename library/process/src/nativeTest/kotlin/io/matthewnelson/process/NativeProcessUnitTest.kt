package io.matthewnelson.process

import io.matthewnelson.process.internal.NativeProcess
import platform.posix.SIGTERM
import platform.posix.kill
import kotlin.experimental.ExperimentalNativeApi

class NativeProcessUnitTest: ProcessNonJsBaseTest() {

    override fun Process.sigterm() {
        val p = (this as NativeProcess)
        kill(p.pid, SIGTERM)
    }

    @OptIn(ExperimentalNativeApi::class)
    override val isUnixDesktop: Boolean = when (Platform.osFamily) {
        OsFamily.MACOSX,
        OsFamily.LINUX -> true
        else -> false
    }
}
