package io.matthewnelson.process

import kotlin.experimental.ExperimentalNativeApi

class NativeProcessUnitTest: ProcessNonJsBaseTest() {

    @OptIn(ExperimentalNativeApi::class)
    override val isUnixDesktop: Boolean = when (Platform.osFamily) {
        OsFamily.MACOSX,
        OsFamily.LINUX -> true
        else -> false
    }
}
