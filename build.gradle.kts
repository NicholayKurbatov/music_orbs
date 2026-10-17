plugins {
    // Общий classloader Kotlin для Android/JVM; версия — в settings.gradle.kts.
    id("com.android.application") apply false
    kotlin("android") apply false
    kotlin("jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
