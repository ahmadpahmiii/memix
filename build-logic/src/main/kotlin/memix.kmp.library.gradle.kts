import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Every Memix module is a KMP library for Android and iOS (no iosX64: Apple silicon only).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val catalog = versionCatalogs.named("libs")
fun versionOf(alias: String) = catalog.findVersion(alias).get().requiredVersion.toInt()

kotlin {
    android {
        namespace = "app.memix." + path.removePrefix(":").replace(':', '.').replace("-", "")
        compileSdk = versionOf("android-compileSdk")
        minSdk = versionOf("android-minSdk")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()
}
