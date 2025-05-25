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
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

@OptIn(ExperimentalForeignApi::class)
class PosixFileActionsUnitTest {

    @Test
    fun givenAddChDirNp_ifAvailable_thenIsSuccessful() {
        val d = PROJECT_DIR_PATH.toFile()
            .resolve("src")
            .resolve("linuxTest")

        val unit = try {
            posixSpawnScopeOrNull(requireChangeDir = false) {
                file_actions_addchdir_np(d).check()
                Unit
            }
        } catch (e: UnsupportedOperationException) {
            if (PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP == null) return // pass
            fail("change dir should be available, but function call threw exception", e)
        }

        // Only way this would be non-null is if host machine is:
        //  - glibc 2.23 or lower (not likely)
        //  - posix_spawn_file_actions_init failed
        //  - posix_spawnattr_init failed
        assertNotNull(unit)
    }
}
