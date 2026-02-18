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
@file:Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import kotlin.time.Duration

internal actual val STDIO_NULL: File = (System.getProperty("os.name")
    ?.ifBlank { null }
    ?.contains("windows", ignoreCase = true)
    ?: (SysDirSep == '\\'))
    .let { isWindows -> if (isWindows) "NUL" else "/dev/null" }
    .toFile()

internal actual val IsDesktop: Boolean get() = ANDROID.SDK_INT == null

@Throws(UnsupportedOperationException::class)
internal actual inline fun Process.Current.platformPID(): Int = JVM_CURRENT_PID

@Throws(IllegalArgumentException::class, InterruptedException::class)
internal actual inline fun Duration.threadSleep() {
    Thread.sleep(inWholeMilliseconds)
}

internal actual inline fun Process.hasStdoutStarted(): Boolean = (this as JvmProcess)._hasStdoutStarted
internal actual inline fun Process.hasStderrStarted(): Boolean = (this as JvmProcess)._hasStderrStarted

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias ReadStream = java.io.InputStream
@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias WriteStream = java.io.OutputStream

private const val PROCFS_SELF = "/proc/self"

// pid is cached here on Jvm/Android, as it is for the current process and will not change.
@get:Throws(UnsupportedOperationException::class)
private val JVM_CURRENT_PID: Int by lazy {
    if (ANDROID.SDK_INT != null) return@lazy try {
        Class.forName("android.os.Process")
            .getMethod("myPid")
            .invoke(null) as Int
    } catch (t: Throwable) {
        try {
            PROCFS_SELF.toFile().canonicalFile2().name.toInt()
        } catch (tt: Throwable) {
            // This should NEVER be the case on Android, as /proc/self always exists
            throw UnsupportedOperationException("Failed to retrieve pid for Android", t)
                .apply { addSuppressed(tt) }
        }
    }

    // Java 9
    try {
        val clazz = Class.forName("java.lang.ProcessHandle")
        val mCurrent = clazz.getMethod("current")
        val mPid = clazz.getMethod("pid")
        if (mCurrent != null && mPid != null) {
            val current = mCurrent.invoke(null)
            if (current != null) {
                return@lazy (mPid.invoke(current) as Long).toInt()
            }
        }
    } catch (_: Throwable) {}

    // Java 10
    var threw: Throwable? = null
    try {
        val mGetPid = java.lang.management.RuntimeMXBean::class.java
            .getMethod("getPid")
        if (mGetPid != null) {
            val mxBean = java.lang.management.ManagementFactory
                .getRuntimeMXBean()
            return@lazy (mGetPid.invoke(mxBean) as Long).toInt()
        }
    } catch (t: Throwable) {
        // NoClassDefFound (i.e. java.management module not declared)
        threw = t
    }

    // Java 8
    try {
        return@lazy java.lang.management.ManagementFactory
            .getRuntimeMXBean()
            .name
            .split('@')[0]
            .toInt()
    } catch (t: Throwable) {
        // NoClassDefFound (i.e. java.management module not declared)
        threw?.addSuppressed(t) ?: run { threw = t }
    }

    // Last resort.
    if (SysDirSep == '/') try {
        val procfs = PROCFS_SELF.toFile()
        if (procfs.exists2()) return@lazy procfs.canonicalFile2().name.toInt()
    } catch (t: Throwable) {
        threw?.addSuppressed(t) ?: run { threw = t }
    }

    throw UnsupportedOperationException("Failed to retrieve pid", threw)
}
