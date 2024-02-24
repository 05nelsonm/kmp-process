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
package io.matthewnelson.kmp.process.testing

import kotlinx.coroutines.test.TestResult
import kotlin.test.Test

class ProcessUnitTest: ProcessBaseTest() {

    @Test
    override fun givenCurrentProcess_whenPid_thenSucceeds() {
        super.givenCurrentProcess_whenPid_thenSucceeds()
    }

    @Test
    override fun givenCurrentProcess_whenEnvironment_thenIsNotEmpty() {
        super.givenCurrentProcess_whenEnvironment_thenIsNotEmpty()
    }

    @Test
    override fun givenExecutable_whenOutputToFile_thenIsAsExpected(): TestResult {
        return super.givenExecutable_whenOutputToFile_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenOutput_thenIsAsExpected() {
        super.givenExecutable_whenOutput_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected(): TestResult {
        return super.givenExecutable_whenPipeOutputFeeds_thenIsAsExpected()
    }
}
