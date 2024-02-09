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
package io.matthewnelson.kmp.process

/**
 * The signal to send when [Process.destroy] is called.
 * */
public enum class Signal {

    /**
     * The default
     *
     * On Jvm, this is the same as calling `java.lang.Process.destroy`
     * */
    SIGTERM,

    /**
     * On Jvm, this is the same as calling `java.lang.Process.destroyForcibly`.
     *
     * Note that on Android API 25 and below, SIGTERM is always utilized
     * as `java.lang.Process.destroyForcibly` is unavailable.
     * */
    SIGKILL
}
