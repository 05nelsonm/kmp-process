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
import co.touchlab.cklib.gradle.CKlibGradleExtension
import co.touchlab.cklib.gradle.CompileToBitcode
import co.touchlab.cklib.gradle.CompileToBitcodeExtension
import com.android.utils.TraceUtils.simpleId
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.TargetSupportException
import org.jetbrains.kotlin.konan.util.ArchiveType
import org.jetbrains.kotlin.konan.util.DependencyProcessor
import org.jetbrains.kotlin.konan.util.DependencySource

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(java9ModuleName = "io.matthewnelson.kmp.process", publish = true) {
        common {
            pluginIds(libs.plugins.cklib.get().pluginId)

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
                    "androidNative",
                    "linux",
                    "macos",
                ).mapNotNull { name ->
                    val main = findByName(name + "Main") ?: return@mapNotNull null
                    main to getByName(name + "Test")
                }

                if (sources.isEmpty()) return@kotlin

                val main = maybeCreate("unixForkMain").apply {
                    dependsOn(getByName("unixMain"))
                }
                val test = maybeCreate("unixForkTest").apply {
                    dependsOn(getByName("unixTest"))
                }
                sources.forEach { (sourceMain, sourceTest) ->
                    sourceMain.dependsOn(main)
                    sourceTest.dependsOn(test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val sources = listOf(
                    "jvm",
                    "js",
                    "mingw",
                    "unixFork",
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
            with(sourceSets) {
                findByName("nonJvmMain")?.apply {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
                findByName("unixForkMain")?.apply {
                    dependencies {
                        implementation(kotlincrypto.bitops.endian)
                    }
                }
            }
        }

        kotlin {
            val cinteropDir = projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")

            val interopTaskInfo = targets.filterIsInstance<KotlinNativeTarget>().map { target ->
                if (target.konanTarget.family == Family.ANDROID) {
                    target.compilations["main"].cinterops.create("kmp_process_sys").apply {
                        definitionFile.set(cinteropDir.resolve("$name.def"))
                        includeDirs(cinteropDir)
                    }
                }
                when (target.konanTarget.family) {
                    Family.ANDROID, Family.OSX, Family.IOS, Family.TVOS, Family.WATCHOS -> {
                        target.compilations["main"].cinterops.create("spawn") {
                            definitionFile.set(cinteropDir.resolve("$name.def"))
                        }
                    }
                    else -> {}
                }

                val syscall = target.compilations["test"].cinterops.create("syscall").apply {
                    definitionFile.set(cinteropDir.resolve("$name.def"))
                }

                Triple(syscall.interopProcessingTaskName, target.konanTarget, target.targetName)
            }

            project.afterEvaluate {
                val commonizeTask = project.tasks.findByName("commonizeCInterop")

                project.tasks.all {
                    when {
                        name.endsWith("MetadataElements") -> if (commonizeTask != null) {
                            dependsOn(commonizeTask)
                        }
                        name.endsWith("TestCinterop-syscallKlib") -> interopTaskInfo.forEach {
                            val (taskName, _, targetName) = it
                            if (!name.startsWith(targetName)) return@forEach
                            dependsOn(taskName)
                        }
                    }
                }
            }

            project.extensions.configure<CompileToBitcodeExtension>("cklib") {
                val downloadDevLLVMTask = config.configure(libs)

                create("kmp_process_sys") {
                    language = CompileToBitcode.Language.C
                    srcDirs = project.files(cinteropDir)
                    includeFiles = listOf("$compileName.c")

                    listOf(
                        "-Wno-unused-command-line-argument",
                    ).let { compilerArgs.addAll(it) }

                    val kt = KonanTarget.predefinedTargets[target]!!

                    // Must add dependency on the test cinterop task to ensure
                    // that Kotlin/Native dependencies get downloaded beforehand
                    interopTaskInfo.forEach { (interopTaskName, konanTarget, _) ->
                        if (kt != konanTarget) return@forEach
                        dependsOn(interopTaskName)
                    }

                    dependsOn(downloadDevLLVMTask)
                }
            }
        }
    }
}

// CKLib uses too old of a version of LLVM for current version of Kotlin which produces errors for android
// native due to unsupported link arguments. Below is a supplemental implementation to download and use
// the -dev llvm compiler for the current kotlin version.
//
// The following info can be found in ~/.konan/kotlin-native-prebuild-{os}-{arch}-{kotlin version}/konan/konan.properties
private object LLVM {
    const val URL: String = "https://download.jetbrains.com/kotlin/native/resources/llvm"
    const val VERSION: String = "19"

    // llvm-{llvm version}-{arch}-{host}-dev-{id}
    object DevID {
        object Linux {
            const val x86_64: Int = 103
        }
        object MacOS {
            const val aarch64: Int = 79
            const val x86_64: Int = 75
        }
        object MinGW {
            const val x86_64: Int = 134
        }
    }
}

private fun CKlibGradleExtension.configure(libs: LibrariesForLibs): Task {
    kotlinVersion = libs.versions.gradle.kotlin.get()
    check(kotlinVersion == "2.2.20") {
        "Kotlin version out of date! Download URLs for LLVM need to be updated for ${project.path}"
    }

    val host = HostManager.simpleOsName()
    val arch = HostManager.hostArch()
    val (id, archive) = when (host) {
        "linux" -> when (arch) {
            "x86_64" -> LLVM.DevID.Linux.x86_64 to ArchiveType.TAR_GZ
            else -> null
        }
        "macos" -> when (arch) {
            "aarch64" -> LLVM.DevID.MacOS.aarch64 to ArchiveType.TAR_GZ
            "x86_64" -> LLVM.DevID.MacOS.x86_64 to ArchiveType.TAR_GZ
            else -> null
        }
        "windows" -> when (arch) {
            "x86_64" -> LLVM.DevID.MinGW.x86_64 to ArchiveType.ZIP
            else -> null
        }
        else -> null
    } ?: throw TargetSupportException("Unsupported host[$host] or arch[$arch]")

    val llvmDev = "llvm-${LLVM.VERSION}-${arch}-${host}-dev-${id}"
    val cklibDir = File(System.getProperty("user.home")).resolve(".cklib")
    llvmHome = cklibDir.resolve(llvmDev).path

    val source = DependencySource.Remote.Public(subDirectory = "${LLVM.VERSION}-${arch}-${host}")

    val processor = DependencyProcessor(
        dependenciesRoot = cklibDir,
        dependenciesUrl = LLVM.URL,
        dependencyToCandidates = mapOf(llvmDev to listOf(source)),
        homeDependencyCache = cklibDir.resolve("cache"),
        customProgressCallback = { _, currentBytes, totalBytes ->
            val total = totalBytes.toString()
            var current = currentBytes.toString()
            while (current.length < 15 && current.length < total.length) {
                current = " $current"
            }

            println("Downloading[$llvmDev] - $current / $total")
        },
        archiveType = archive,
    )

    return project.tasks.register("downloadDevLLVM") {
        actions = listOf(Action {
            processor.run()
        })
    }.get()
}
