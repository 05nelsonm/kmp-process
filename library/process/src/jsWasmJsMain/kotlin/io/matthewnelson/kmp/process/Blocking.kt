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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "RedundantVisibilityModifier")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException

/**
 * Extended by [OutputFeed.Handler] (which is extended by [Process]) in order to provide
 * blocking APIs for Jvm & Native.
 * */
public actual sealed class Blocking protected actual constructor() {

    /**
     * Extended by [OutputFeed.Waiter] in order to provide blocking APIs for Jvm & Native.
     * */
    public actual sealed class Waiter actual constructor(
        /** @suppress */
        protected actual val process: Process,
    ) {

        /** @suppress */
        protected actual abstract fun isStarted(): Boolean
        /** @suppress */
        protected actual abstract fun isStopped(): Boolean
    }

    /**
     * Extended by [Process.Builder] in order to provide blocking APIs for Jvm/Native
     * */
    public actual sealed class Builder protected actual constructor() {

        /** @suppress */
        @Throws(IOException::class)
        protected actual abstract fun createProcessProtected(): Process
    }
}
