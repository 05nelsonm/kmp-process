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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal.spawn

internal actual class GnuLibcVersion private constructor() {

    internal actual fun isAtLeast(
        major: UByte,
        minor: UByte,
    ): Boolean {
        throw UnsupportedOperationException()
    }

    internal actual companion object {

        @Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
        internal actual fun check(block: GnuLibcVersion.() -> Unit) { /* no-op */ }
    }
}
