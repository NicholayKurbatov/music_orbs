plugins {
    kotlin("jvm")
}

dependencyLocking { lockAllConfigurations() }

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDirs("../android/audio", "../android/motion", "../android/experience")
            kotlin.exclude("**/src/test/**", "**/platform/**")
        }
        test {
            kotlin.srcDirs(
                "../android/audio/src/test/kotlin",
                "../android/motion/src/test/kotlin",
                "../android/experience/src/test/kotlin",
                "../tests/contracts/kotlin"
            )
        }
    }
}

dependencies {
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    workingDir = rootProject.projectDir
    testLogging { events("failed", "skipped") }
}
