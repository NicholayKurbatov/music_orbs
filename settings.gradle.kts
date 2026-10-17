pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.2"
        kotlin("android") version "2.1.21"
        kotlin("jvm") version "2.1.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "MusicOrbs"
include(":core")
if (!providers.gradleProperty("jvmOnly").isPresent) {
    include(":app")
    project(":app").projectDir = file("android")
}
