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

import kotlin.jvm.JvmSynthetic

// Because Process is an abstract class, it
// could be extended from Java land as the
// internal constructor compiles to public.
//
// This is an "attempt" to inhibit Java only
// consumers from being able to extend Process
// as the only way to obtain is through
// synthetic access via get().
internal class SyntheticAccess private constructor() {

    internal companion object {

        private val instance = SyntheticAccess()

        @JvmSynthetic
        internal fun get(): SyntheticAccess = instance
    }
}
