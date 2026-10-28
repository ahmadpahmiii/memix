plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        implementation(libs.kotlinx.collections.immutable)
        // The import sheet: system back before it shows, and a space check when the app returns to the foreground.
        implementation(libs.jetbrains.navigationevent.compose)
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
