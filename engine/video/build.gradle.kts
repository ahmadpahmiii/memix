plugins {
    id("memix.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
    // Media3 renders on Android; iOS gets AVFoundation in Phase 7 (P7-03 to P7-05).
    sourceSets.androidMain.dependencies {
        implementation(libs.media3.transformer)
        implementation(libs.media3.effect)
        implementation(libs.media3.common)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.koin.android)
    }
}
