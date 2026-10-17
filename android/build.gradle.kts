plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencyLocking { lockAllConfigurations() }

android {
    namespace = "musicorbs.platform"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    buildFeatures { compose = true }

    defaultConfig {
        applicationId = "musicorbs.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].apply {
        manifest.srcFile("platform/AndroidManifest.xml")
        java.srcDirs("platform/kotlin", "bootstrap", "experience/presentation/platform")
        assets.srcDir(layout.buildDirectory.dir("generated/graphicsAssets"))
    }
}

// P2 создаёт graphics/dist. До этого APK честно показывает отдельный shell.
val prepareWebAssets by tasks.registering(Sync::class) {
    val graphicsDist = rootProject.layout.projectDirectory.dir("graphics/dist")
    inputs.property("graphicsAvailable", providers.provider { graphicsDist.file("index.html").asFile.exists() })
    from(provider {
        if (graphicsDist.file("index.html").asFile.exists()) graphicsDist else layout.projectDirectory.dir("platform/assets")
    })
    into(layout.buildDirectory.dir("generated/graphicsAssets"))
}
tasks.named("preBuild").configure { dependsOn(prepareWebAssets) }

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
}
