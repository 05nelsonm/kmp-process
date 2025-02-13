rootProject.name = "kmp-process"

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories { mavenCentral() }

    val vCatalogKC = rootDir
        .resolve("gradle")
        .resolve("libs.versions.toml")
        .readLines()
        .first { it.startsWith("kotlincrypto-catalog ") }
        .substringAfter('"')
        .substringBeforeLast('"')

    versionCatalogs {
        create("kotlincrypto") {
            // https://github.com/KotlinCrypto/version-catalog/blob/master/gradle/kotlincrypto.versions.toml
            from("org.kotlincrypto:version-catalog:$vCatalogKC")
        }
    }
}

includeBuild("build-logic")

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings

if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
} else {
    listOf(
        "process",
        "process-testing",
    ).forEach { module ->
        include(":library:$module")
    }
}
