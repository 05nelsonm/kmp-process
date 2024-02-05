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
@file:JvmName("ProcessExt")

package io.matthewnelson.process

import io.matthewnelson.process.internal.commonWaitFor
import io.matthewnelson.process.internal.threadSleep
import kotlin.jvm.JvmName
import kotlin.time.Duration

/**
 * Blocks the current thread for the specified [timeout],
 * or until an [Process.exitCode] is available.
 *
 * @param [timeout] the [Duration] to wait
 * @return The [Process.exitCode], or null if [timeout] is exceeded
 * */
// Jvm/Native
public fun Process.waitFor(
    timeout: Duration,
): Int? = commonWaitFor(timeout, ::threadSleep)
