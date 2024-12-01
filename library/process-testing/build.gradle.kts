import io.matthewnelson.kmp.tor.common.api.GeoipFiles
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import org.jetbrains.kotlin.konan.target.HostManager

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
plugins {
    id("configuration")
}

repositories {
    google()
}

kmpConfiguration {
    configureShared {
        androidLibrary {
            android {
                buildToolsVersion = "34.0.0"
                compileSdk = 34
                namespace = "io.matthewnelson.kmp.process.testing"

                defaultConfig {
                    minSdk = 15

                    testInstrumentationRunnerArguments["disableAnalytics"] = true.toString()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            kotlinJvmTarget = JavaVersion.VERSION_1_8
            compileSourceCompatibility = JavaVersion.VERSION_1_8
            compileTargetCompatibility = JavaVersion.VERSION_1_8

            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.android.unit.test.tor)
                }
            }
        }

        js {
            sourceSetTest {
                dependencies {
                    implementation(npm("kmp-tor.resource-exec-tor.all", libs.versions.kmp.tor.resource.get()))
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation(project(":library:process"))
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.exec.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            val iosTest = sourceSets.findByName("iosTest") ?: return@kotlin

            val buildDir = project
                .layout
                .buildDirectory
                .asFile
                .get()

            var files: Pair<GeoipFiles, File>? = null

            // Only extract resources when on macOS machine (ios simulator targets are enabled)
            if (HostManager.hostIsMac) {
                val resourceDir = buildDir
                    .resolve("kmp-tor-resource")
                    .resolve("macos")

                val loader = ResourceLoaderTorExec.getOrCreate(resourceDir) as ResourceLoader.Tor.Exec
                files = loader.extract() to loader.process(BINDER) { tor, _ -> tor }
            }

            val kotlinSrc = buildDir
                .resolve("generated")
                .resolve("sources")
                .resolve("testConfig")
                .resolve("iosTest")
                .resolve("kotlin")

            val pkgDir = kotlinSrc.resolve("io")
                .resolve("matthewnelson")
                .resolve("kmp")
                .resolve(project.name.replace('-', File.separatorChar))

            pkgDir.mkdirs()

            pkgDir.resolve("TestIosConfig.kt").writeText("""
                package io.matthewnelson.kmp.${project.name.replace('-', '.')}

                internal const val IOS_TOR_EXECUTABLE: String = "${files?.second?.path ?: ""}"
                internal const val IOS_TOR_GEOIP: String = "${files?.first?.geoip?.path ?: ""}"
                internal const val IOS_TOR_GEOIP6: String = "${files?.first?.geoip6?.path ?: ""}"

            """.trimIndent())

            iosTest.kotlin.srcDir(kotlinSrc)
        }
    }
}

private object BINDER: ResourceLoader.RuntimeBinder
