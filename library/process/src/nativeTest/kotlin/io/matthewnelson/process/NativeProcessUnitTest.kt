package io.matthewnelson.process

import kotlin.experimental.ExperimentalNativeApi

class NativeProcessUnitTest: ProcessBaseTest() {

    @OptIn(ExperimentalNativeApi::class)
    override val isUnixDesktop: Boolean = when (Platform.osFamily) {
        OsFamily.MACOSX,
        OsFamily.LINUX -> true
        else -> false
    }
    override val isNodeJS: Boolean = false
    @OptIn(ExperimentalNativeApi::class)
    override val isDarwinMobile: Boolean = when (Platform.osFamily) {
        OsFamily.IOS,
        OsFamily.TVOS,
        OsFamily.WATCHOS -> true
        else -> false
    }
}
