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
                    implementation(libs.kotlinx.coroutines.core)
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
                findByName("unixMain")?.apply {
                    dependencies {
                        implementation(libs.kotlincrypto.endians)
                    }
                }

                val jvmMain = findByName("jvmMain")
                val nativeMain = findByName("nativeMain")

                if (nativeMain != null || jvmMain != null) {
                    val nonJsMain = maybeCreate("blockingMain")
                    nonJsMain.dependsOn(getByName("commonMain"))
                    jvmMain?.apply { dependsOn(nonJsMain) }
                    nativeMain?.apply { dependsOn(nonJsMain) }

                    val nonJsTest = maybeCreate("blockingTest")
                    nonJsTest.dependsOn(getByName("commonTest"))
                    findByName("jvmTest")?.apply { dependsOn(nonJsTest) }
                    findByName("nativeTest")?.apply { dependsOn(nonJsTest) }
                }
            }

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
            defFile = cinteropDir.resolve("glibc_version.def")
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
            defFile = cinteropDir.resolve("spawn.def")
        }
    }

    return this
}
