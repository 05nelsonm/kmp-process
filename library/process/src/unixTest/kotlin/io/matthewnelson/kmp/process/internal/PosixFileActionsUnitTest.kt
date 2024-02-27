/*
 * Copyright (c) 2024 Matthew Nelson
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
package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.PROJECT_DIR_PATH
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalForeignApi::class)
class PosixFileActionsUnitTest {

    private val chdirIsAvailable: Boolean by lazy {
        var available: Boolean? = null
        GnuLibcVersion.check {
            // does nothing on non-Linux
            available = isAtLeast(major = 2u, minor = 29u)
        }

        available ?: true
    }

    @Test
    fun givenAddChDirNp_ifAvailable_thenIsSuccessful() {
        val d = PROJECT_DIR_PATH.toFile()
            .resolve("src")
            .resolve("linuxTest")

        try {
            memScoped {
                posixSpawnFileActionsInit()
                    .addchdir_np(d, this).check()
            }
        } catch (e: UnsupportedOperationException) {
            if (!chdirIsAvailable) return
            fail("change dir should be available, but function call threw exception", e)
        }
    }
}
