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
        common {
            sourceSetMain {
                dependencies {
                    api(libs.kmp.file)
                    implementation(libs.immutable.collections)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val jvmMain = findByName("jvmMain")
                val nativeMain = findByName("nativeMain")

                if (nativeMain != null || jvmMain != null) {
                    val nonJsMain = maybeCreate("nonJsMain")
                    nonJsMain.dependsOn(getByName("commonMain"))
                    jvmMain?.apply { dependsOn(nonJsMain) }
                    nativeMain?.apply { dependsOn(nonJsMain) }

                    val nonJsTest = maybeCreate("nonJsTest")
                    nonJsTest.dependsOn(getByName("commonTest"))
                    findByName("jvmTest")?.apply { dependsOn(nonJsTest) }
                    findByName("nativeTest")?.apply { dependsOn(nonJsTest) }
                }
            }
            targets.filterIsInstance<KotlinNativeTarget>().spawnCInterop()
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
