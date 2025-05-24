package io.matthewnelson.kmp.process.internal.spawn

import kotlin.test.Test
import kotlin.test.assertFailsWith

class SpawnIosUnitTest {

    @Test
    fun givenPosixSpawnScopeOrNull_whenChangeDirRequired_thenThrowsException() {
        assertFailsWith<UnsupportedOperationException> {
            posixSpawnScopeOrNull(requireChangeDir = true) {}
        }
    }
}
