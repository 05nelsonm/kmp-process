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
import com.android.build.gradle.tasks.MergeSourceSetFolders
import io.matthewnelson.kmp.tor.common.api.GeoipFiles
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    id("configuration")
}

repositories { google() }

if (!HostManager.hostIsMac) {
    extraProperties.set("kotlin.native.enableKlibsCrossCompilation", false.toString())
}

kmpConfiguration {
    configureShared {
        val jniLibsDir = projectDir
            .resolve("src")
            .resolve("androidInstrumentedTest")
            .resolve("jniLibs")

        project.tasks.all {
            if (name != "clean") return@all
            doLast { jniLibsDir.deleteRecursively() }
        }

        androidLibrary {
            android {
                buildToolsVersion = "35.0.1"
                compileSdk = 35
                namespace = "io.matthewnelson.kmp.process.test.api"

                defaultConfig {
                    minSdk = 15

                    testInstrumentationRunnerArguments["disableAnalytics"] = true.toString()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                sourceSets["androidTest"].jniLibs.srcDir(jniLibsDir)
            }

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
            with(sourceSets) {
                arrayOf("js", "wasmJs").forEach { name ->
                    findByName(name + "Test")?.dependencies {
                        var v = libs.versions.kmp.tor.resource.get()
                        if (v.endsWith("-SNAPSHOT")) {
                            v += libs.versions.kmp.tor.resourceNpmSNAPSHOT.get()
                        }
                        implementation(npm("kmp-tor.resource-exec-tor.all", v))
                    }
                }
            }
        }

        kotlin {
            val iosTest = sourceSets.findByName("iosTest") ?: return@kotlin

            val buildDir = project
                .layout
                .buildDirectory
                .asFile.get()

            val kotlinSrc = buildDir
                .resolve("generated")
                .resolve("sources")
                .resolve("testConfig")
                .resolve("iosTest")
                .resolve("kotlin")

            val pkgDir = kotlinSrc.resolve("io")
                .resolve("matthewnelson")
                .resolve("kmp")
                .resolve("process")
                .resolve(project.name.replace('-', File.separatorChar))

            pkgDir.mkdirs()

            project.afterEvaluate {
                var files: Pair<GeoipFiles, File>? = null

                // Only extract resources when on macOS machine (ios simulator targets are enabled)
                if (HostManager.hostIsMac) {
                    val resourceDir = buildDir
                        .resolve("kmp-tor-resource")
                        .resolve("macos")

                    val loader = ResourceLoaderTorExec.getOrCreate(resourceDir) as ResourceLoader.Tor.Exec
                    files = loader.extract() to loader.process(BINDER) { tor, _ -> tor }
                }

                pkgDir.resolve("TestIosConfig.kt").writeText("""
                    package io.matthewnelson.kmp.process.${project.name.replace('-', '.')}
    
                    internal const val IOS_TOR_EXECUTABLE: String = "${files?.second?.path ?: ""}"
                    internal const val IOS_TOR_GEOIP: String = "${files?.first?.geoip?.path ?: ""}"
                    internal const val IOS_TOR_GEOIP6: String = "${files?.first?.geoip6?.path ?: ""}"
    
                """.trimIndent())
            }

            iosTest.kotlin.srcDir(kotlinSrc)
        }

        kotlin {
            if (!project.plugins.hasPlugin("com.android.base")) return@kotlin

            try {
                project.evaluationDependsOn(":library:process")
            } catch (_: Throwable) {}

            project.afterEvaluate {
                val nativeTestBinaryTasks = arrayOf(
                    project to "libTestApiExec.so",
                    project(":library:process") to "libTestProcessExec.so"
                ).flatMap { (project, libName) ->

                    val buildDir = project
                        .layout
                        .buildDirectory
                        .asFile.get()

                    arrayOf(
                        "Arm32" to "armeabi-v7a",
                        "Arm64" to "arm64-v8a",
                        "X64" to "x86_64",
                        "X86" to "x86",
                    ).mapNotNull { (arch, abi) ->
                        val nativeTestBinariesTask = project
                            .tasks
                            .findByName("androidNative${arch}TestBinaries")
                            ?: return@mapNotNull null

                        val abiDir = jniLibsDir.resolve(abi)
                        if (!abiDir.exists() && !abiDir.mkdirs()) throw RuntimeException("mkdirs[$abiDir]")

                        val testExecutable = buildDir
                            .resolve("bin")
                            .resolve("androidNative$arch")
                            .resolve("debugTest")
                            .resolve("test.kexe")

                        nativeTestBinariesTask.doLast {
                            testExecutable.copyTo(abiDir.resolve(libName), overwrite = true)
                        }

                        nativeTestBinariesTask
                    }
                }

                project.tasks.withType(MergeSourceSetFolders::class.java).all {
                    if (name != "mergeDebugAndroidTestJniLibFolders") return@all
                    nativeTestBinaryTasks.forEach { task -> dependsOn(task) }
                }
            }
        }
    }
}

private object BINDER: ResourceLoader.RuntimeBinder
