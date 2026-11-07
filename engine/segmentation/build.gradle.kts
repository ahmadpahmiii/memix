plugins {
    id("memix.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
    // ML Kit Subject Segmentation: the model is not in the APK, Google Play services downloads it on first use (P2-06).
    sourceSets.androidMain.dependencies {
        implementation(libs.mlkit.subject.segmentation)
        implementation(libs.play.services.base)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.kotlinx.coroutines.play.services)
        implementation(libs.koin.android)
    }
}
