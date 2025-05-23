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
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(java9ModuleName = "io.matthewnelson.kmp.process", publish = true) {
        common {
            sourceSetMain {
                dependencies {
                    api(libs.kmp.file)
                    implementation(libs.immutable.collections)
                    compileOnly(libs.kotlinx.coroutines.core)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val sources = listOf(
                    "jvm",
                    "js",
                    "androidNative",
                    "linux",
                    "macos",
                    "mingw",
                ).mapNotNull { name ->
                    val main = findByName(name + "Main") ?: return@mapNotNull null
                    main to getByName(name + "Test")
                }

                if (sources.isEmpty()) return@kotlin

                val main = maybeCreate("nonAppleMobileMain").apply {
                    dependsOn(getByName("commonMain"))
                }
                val test = maybeCreate("nonAppleMobileTest").apply {
                    dependsOn(getByName("commonTest"))
                }
                sources.forEach { (sourceMain, sourceTest) ->
                    sourceMain.dependsOn(main)
                    sourceTest.dependsOn(test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                findByName("nonJvmMain")?.apply {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
                findByName("unixMain")?.apply {
                    dependencies {
                        implementation(kotlincrypto.bitops.endian)
                    }
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val jvmMain = findByName("jvmMain")
                val nativeMain = findByName("nativeMain")

                if (nativeMain != null || jvmMain != null) {
                    val blockingMain = maybeCreate("blockingMain")
                    blockingMain.dependsOn(getByName("commonMain"))
                    jvmMain?.apply { dependsOn(blockingMain) }
                    nativeMain?.apply { dependsOn(blockingMain) }

                    val blockingTest = maybeCreate("blockingTest")
                    blockingTest.dependsOn(getByName("commonTest"))
                    findByName("jvmTest")?.apply { dependsOn(blockingTest) }
                    findByName("nativeTest")?.apply { dependsOn(blockingTest) }
                }
            }

        }

        kotlin {
            val cinteropDir = projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")

            targets.filterIsInstance<KotlinNativeTarget>()
                .glibc_versionCInterop(cinteropDir)
                .spawnCInterop(cinteropDir)
        }
    }
}

@Suppress("FunctionName")
fun List<KotlinNativeTarget>.glibc_versionCInterop(
    cinteropDir: File,
): List<KotlinNativeTarget> {
    forEach { target ->
        if (target.konanTarget.family != Family.LINUX) return@forEach

        target.compilations["main"].cinterops.create("glibc_version").apply {
            definitionFile.set(cinteropDir.resolve("glibc_version.def"))
        }
    }

    return this
}

fun List<KotlinNativeTarget>.spawnCInterop(
    cinteropDir: File,
): List<KotlinNativeTarget> {
    if (!HostManager.hostIsMac) return this

    forEach { target ->
        if (!target.konanTarget.family.isAppleFamily) return@forEach

        target.compilations["main"].cinterops.create("spawn").apply {
            definitionFile.set(cinteropDir.resolve("spawn.def"))
        }
    }

    return this
}
