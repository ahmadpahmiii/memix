import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("memix.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "app.memix.ComposeApp")
        }
    }
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.designsystem)
        implementation(projects.core.ui)
        implementation(projects.core.data)
        implementation(projects.engine.video)
        implementation(projects.engine.photo)
        implementation(projects.engine.segmentation)
        implementation(projects.platform.services)
        implementation(projects.feature.onboarding)
        implementation(projects.feature.home)
        implementation(projects.feature.templates)
        implementation(projects.feature.sounds)
        implementation(projects.feature.drafts)
        implementation(projects.feature.videoEditor)
        implementation(projects.feature.photoEditor)
        implementation(projects.feature.export)
        implementation(libs.jetbrains.navigation.compose)
        implementation(libs.jetbrains.lifecycle.runtime.compose)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
        implementation(libs.koin.compose.viewmodel)
    }
    // The composition root opens :core:data's database with each platform's driver (DatabaseDriverModule).
    sourceSets.androidMain.dependencies {
        implementation(libs.koin.android)
        implementation(libs.sqldelight.android.driver)
    }
    sourceSets.iosMain.dependencies {
        implementation(libs.sqldelight.native.driver)
    }
}
