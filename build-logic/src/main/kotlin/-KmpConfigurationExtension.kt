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
import io.matthewnelson.kmp.configuration.extension.KmpConfigurationExtension
import io.matthewnelson.kmp.configuration.extension.container.target.KmpConfigurationContainerDsl
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File

fun KmpConfigurationExtension.configureShared(
    java9ModuleName: String? = null,
    publish: Boolean = false,
    action: Action<KmpConfigurationContainerDsl>
) {
    if (publish) {
        require(!java9ModuleName.isNullOrBlank()) { "publications must specify a module-info name" }
    }

    configure {
        options {
            useUniqueModuleNames = true
        }

        jvm {
            kotlinJvmTarget = JavaVersion.VERSION_1_8
            compileSourceCompatibility = JavaVersion.VERSION_1_8
            compileTargetCompatibility = JavaVersion.VERSION_1_8

            // windows always throws a fit if not using Java 11. This disables
            // compilation of module-info.java. Nobody deploys from Windows
            // anyway...
            if (!HostManager.hostIsMingw) {
                java9ModuleInfoName = java9ModuleName
            }
        }

        js {
            target {
                nodejs {
                    testTask {
                        useMocha { timeout = "30s" }
                    }
                }
            }
        }

        androidNativeAll()
        iosAll()
        linuxAll()
        macosAll()
//        mingwAll()

        // posix_spawn is "supported" but APIs for posix_spawn_file_actions
        // and posix_spawnattr are unavailable
//        tvosAll()
//        watchosAll()

        common {
            if (publish) pluginIds("publication", "dokka")

            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin {
            explicitApi()

            val project = targets.first().project

            val kotlinSrc = project
                .layout
                .buildDirectory
                .get()
                .asFile
                .resolve("generated")
                .resolve("sources")
                .resolve("testConfig")
                .resolve("commonTest")
                .resolve("kotlin")

            val pkgDir = kotlinSrc.resolve("io")
                .resolve("matthewnelson")
                .resolve("kmp")
                .resolve(project.name.replace('-', File.separatorChar))

            pkgDir.mkdirs()

            pkgDir.resolve("TestConfig.kt").writeText("""
                package io.matthewnelson.kmp.${project.name.replace('-', '.')}
                
                internal const val PROJECT_DIR_PATH: String = "${project.projectDir.canonicalPath.replace("\\", "\\\\")}"

            """.trimIndent())

            sourceSets.commonTest.get().kotlin.srcDir(kotlinSrc)
        }

        action.execute(this)
    }
}
