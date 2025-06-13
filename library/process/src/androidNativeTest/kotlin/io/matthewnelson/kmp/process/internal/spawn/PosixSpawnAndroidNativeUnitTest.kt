/*
 * Copyright (c) 2025 Matthew Nelson
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
package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.PROJECT_DIR_PATH
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.IS_POSIX_SPAWN_AVAILABLE
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.android_get_device_api_level
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(DoNotReferenceDirectly::class, ExperimentalForeignApi::class)
class PosixSpawnAndroidNativeUnitTest {

    @Test
    fun givenDeviceApi_when28OrGreater_thenPosixSpawnIsAvailable() {
        val result = posixSpawnScopeOrNull(requireChangeDir = false) { Unit }
        if (android_get_device_api_level() >= 28) {
            assertNotNull(result)
            assertTrue(IS_POSIX_SPAWN_AVAILABLE)
        } else {
            assertNull(result)
            assertFalse(IS_POSIX_SPAWN_AVAILABLE)
        }
    }

    @Test
    fun givenDeviceApi_when34OrGreater_thenChdirIsAvailable() {
        val result = posixSpawnScopeOrNull(requireChangeDir = true) { Unit }
        if (android_get_device_api_level() >= 34) {
            assertNotNull(result)
        } else {
            assertNull(result)
        }
    }

    @Test
    fun givenAddChDirNp_ifAvailable_thenIsSuccessful() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            println("Skipping...")
            return
        }

        val d = PROJECT_DIR_PATH.toFile()
            .resolve("src")
            .resolve("androidNativeTest")

        val unit = try {
            posixSpawnScopeOrNull(requireChangeDir = false) {
                file_actions_addchdir_np(d).check { it == 0 }
                Unit
            }
        } catch (e: UnsupportedOperationException) {
            if (PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP == null) return // pass
            fail("change dir should be available, but function call threw exception", e)
        }

        // Only way this would be non-null is if:
        //  - posix_spawn_file_actions_init failed
        //  - posix_spawnattr_init failed
        assertNotNull(unit)
    }
}
