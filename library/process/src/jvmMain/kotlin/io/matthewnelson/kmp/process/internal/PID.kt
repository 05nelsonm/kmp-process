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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import kotlin.concurrent.Volatile

internal actual object PID {

    @Volatile
    private var SKIP_JAVA9: Boolean = false

    @JvmSynthetic
    internal actual fun get(): Int {
        androidOrNull()?.let { return it }

        if (!SKIP_JAVA9) {
            // Firstly, check java 9 availability
            try {
                java9OrNull()
            } catch (_: Throwable) {
                // SecurityException or UnsupportedOperationException
                null
            }?.let { return it }

            // Failure. Skip it for successive calls
            SKIP_JAVA9 = true
        }

        // Lastly, access java.management module
        return java10OrNull() ?: java8()
    }

    @JvmSynthetic
    internal fun androidOrNull(): Int? {
        val method = AndroidMyPidMethod ?: return null
        return method.invoke(null) as Int
    }

    @JvmSynthetic
    internal fun java10OrNull(): Int? {
        val method = RuntimeMXBeanGetPidMethod ?: return null
        val mxBean = java.lang.management.ManagementFactory
            .getRuntimeMXBean()
        return (method.invoke(mxBean) as Long).toInt()
    }

    @JvmSynthetic
    @Throws(SecurityException::class, UnsupportedOperationException::class)
    internal fun java9OrNull(): Int? {
        val current = ProcessHandleCurrentMethod?.invoke(null) ?: return null
        val pid = ProcessHandlePidMethod?.invoke(current) ?: return null
        return (pid as Long).toInt()
    }

    // Throws NoClassDefFoundError on Android
    @JvmSynthetic
    internal fun java8(): Int {
        return java.lang.management.ManagementFactory
            .getRuntimeMXBean()
            .name
            .split('@')[0]
            .toInt()
    }

    // Android
    private val AndroidMyPidMethod by lazy {
        if (!IsMobile) return@lazy null
        Class.forName("android.os.Process")
            .getMethod("myPid")
    }

    // Java 9
    private val ProcessHandleClass by lazy {
        try {
            Class.forName("java.lang.ProcessHandle")
        } catch (_: Throwable) {
            null
        }
    }

    private val ProcessHandlePidMethod by lazy {
        ProcessHandleClass?.getMethod("pid")
    }

    private val ProcessHandleCurrentMethod by lazy {
        ProcessHandleClass?.getMethod("current")
    }

    // Java 10+
    private val RuntimeMXBeanGetPidMethod by lazy {
        try {
            java.lang.management.RuntimeMXBean::class.java
                .getMethod("getPid")
        } catch (_: Throwable) {
            null
        }
    }
}
