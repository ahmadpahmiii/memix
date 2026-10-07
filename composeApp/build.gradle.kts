import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("memix.kmp.compose")
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
    }
}
