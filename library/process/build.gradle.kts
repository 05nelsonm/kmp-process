/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.Xcode

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(publish = true) {
        kotlin {
            targets.filterIsInstance<KotlinNativeTarget>().spawnCInterop()

            with(sourceSets) {
                val androidNativeMain = findByName("androidNativeMain")
                val linuxMain = findByName("linuxMain")
                val macosMain = findByName("macosMain")

                if (androidNativeMain != null || linuxMain != null || macosMain != null) {
                    val nativeMain = getByName("nativeMain")
                    val forkMain = maybeCreate("forkMain").apply { dependsOn(nativeMain) }
                    androidNativeMain?.apply { dependsOn(forkMain) }
                    linuxMain?.apply { dependsOn(forkMain) }
                    macosMain?.apply { dependsOn(forkMain) }
                }

                val iosMain = findByName("iosMain")
                val tvosMain = findByName("tvosMain")
                val watchosMain = findByName("watchosMain")

                if (iosMain != null || tvosMain != null || watchosMain != null) {
                    val darwinMain = getByName("darwinMain")
                    val spawnMain = maybeCreate("spawnMain").apply { dependsOn(darwinMain) }
                    iosMain?.apply { dependsOn(spawnMain) }
                    tvosMain?.apply { dependsOn(spawnMain) }
                    watchosMain?.apply { dependsOn(spawnMain) }
                }
            }
        }
    }
}

fun List<KotlinNativeTarget>.spawnCInterop() {
    if (!HostManager.hostIsMac) return
    val xcode = Xcode.findCurrent()

    forEach { target ->
        val sdkInclude = when (target.konanTarget) {
            is KonanTarget.IOS_ARM64 -> xcode.iphoneosSdk
            is KonanTarget.IOS_SIMULATOR_ARM64,
            is KonanTarget.IOS_X64 -> xcode.iphonesimulatorSdk

            is KonanTarget.MACOS_ARM64,
            is KonanTarget.MACOS_X64 -> xcode.macosxSdk

            is KonanTarget.TVOS_ARM64 -> xcode.appletvosSdk
            is KonanTarget.TVOS_SIMULATOR_ARM64,
            is KonanTarget.TVOS_X64 -> xcode.appletvsimulatorSdk

            is KonanTarget.WATCHOS_ARM32,
            is KonanTarget.WATCHOS_ARM64,
            is KonanTarget.WATCHOS_DEVICE_ARM64 -> xcode.watchosSdk
            is KonanTarget.WATCHOS_SIMULATOR_ARM64,
            is KonanTarget.WATCHOS_X64 -> xcode.watchsimulatorSdk
            else -> return@forEach
        }.let { sdkPath ->
            File(sdkPath)
                .resolve("usr")
                .resolve("include")
        }

        target.compilations["main"].cinterops.create("spawn").apply {
            includeDirs(sdkInclude.path)
            defFile = projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")
                .resolve("spawn.def")
        }
    }
}
