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
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    } else {
        maven("https://ossrh-staging-api.central.sonatype.com/service/local/") {
            val p = rootProject.properties

            credentials {
                username = p["mavenCentralUsername"]?.toString()
                password = p["mavenCentralPassword"]?.toString()
            }
        }
    }
}

kmpConfiguration {
    configureShared {
        common {
            sourceSetMain {
                dependencies {
                    implementation("$group:process:$version")
                }
            }
        }
    }
}
