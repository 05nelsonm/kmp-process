@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.process.posix_spawnattr_destroy
import io.matthewnelson.process.posix_spawnattr_init
import io.matthewnelson.process.posix_spawnattr_tVar
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
internal actual value class PosixSpawnAttrs private actual constructor(
    private val _ref: CValuesRef<*>,
) {

    internal val ref: CValuesRef<posix_spawnattr_tVar> get() {
        @Suppress("UNCHECKED_CAST")
        return _ref as CValuesRef<posix_spawnattr_tVar>
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun MemScope.posixSpawnAttrInit(): PosixSpawnAttrs {
            val attrs = alloc<posix_spawnattr_tVar>()
            posix_spawnattr_init(attrs.ptr).check()
            defer { posix_spawnattr_destroy(attrs.ptr) }
            return PosixSpawnAttrs(attrs.ptr)
        }
    }
}
