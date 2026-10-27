import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Every Memix module is a KMP library for Android and iOS (no iosX64: Apple silicon only). Android Lint only analyzes
// a KMP library module that applies com.android.lint (AGP 9); with it, `./gradlew lint` checks each module's androidMain
// and commonMain code, and the app's checkDependencies reaches them too.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.android.lint")
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
