plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "app.memix.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.memix"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        // For measuring speed on a phone (P1-04): release code, signed with the debug key so it installs from a
        // laptop over a debug install (keeping its data), profileable from the shell (src/benchmark), and with the
        // hand-check hooks on (res/values/hand_checks.xml). Never uploaded to Play.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    lint {
        checkDependencies = true // the KMP library modules have no lint task of their own
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    // Reports leaked screens, ViewModels and views on the phone in debug builds; nothing ships in release.
    debugImplementation(libs.leakcanary.android)
}
