plugins {
    // Compose for the pictures the scene draws (ImageBitmap); the drawing function itself is in :core:ui.
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.koin.android)
    }
}
