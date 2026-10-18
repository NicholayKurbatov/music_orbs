plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencyLocking { lockAllConfigurations() }

// UTP работает на хосте: закрываем известные CVE без добавления библиотек в APK.
configurations.configureEach {
    if (name.startsWith("_internal-unified-test-platform-")) {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "io.netty" -> useVersion("4.1.139.Final")
                "com.google.protobuf" -> useVersion("3.25.9")
            }
        }
    }
}

android {
    namespace = "musicorbs.platform"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    buildFeatures { compose = true }

    defaultConfig {
        applicationId = "musicorbs.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].apply {
        manifest.srcFile("platform/AndroidManifest.xml")
        java.srcDirs("platform/kotlin", "bootstrap", "experience/presentation/platform")
        res.srcDir("platform/res")
        assets.srcDir(layout.buildDirectory.dir("generated/graphicsAssets"))
    }
    sourceSets["androidTest"].java.srcDir("platform/src/androidTest/kotlin")
    sourceSets["debug"].apply {
        manifest.srcFile("platform/src/debug/AndroidManifest.xml")
        java.srcDir("platform/src/debug/kotlin")
    }
}

// P2 создаёт graphics/dist. До этого APK честно показывает отдельный shell.
val prepareWebAssets by tasks.registering(Sync::class) {
    val graphicsDist = rootProject.layout.projectDirectory.dir("graphics/dist")
    inputs.property("graphicsAvailable", providers.provider { graphicsDist.file("index.html").asFile.exists() })
    from(provider {
        if (graphicsDist.file("index.html").asFile.exists()) graphicsDist else layout.projectDirectory.dir("platform/assets")
    }) { exclude("smoke/**") }
    from(rootProject.file("third_party")) {
        include("Commons-Math-*", "Kotlin-LICENSE.txt", "Coroutines-LICENSE.txt", "Serialization-LICENSE.txt", "Three-LICENSE", "Thinking-Orbs-LICENSE", "runtime.md")
        into("third_party")
    }
    into(layout.buildDirectory.dir("generated/graphicsAssets"))
}
tasks.named("preBuild").configure { dependsOn(prepareWebAssets) }

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
