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
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation(project(":library:process"))
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }
    }
}
