plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        implementation(libs.kotlinx.collections.immutable)
        // System back while a panel is open or a gesture runs, and the app going to the background.
        implementation(libs.jetbrains.navigationevent.compose)
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
