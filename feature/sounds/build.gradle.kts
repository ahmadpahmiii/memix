plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.model)
        implementation(projects.core.ui)
        implementation(libs.kotlinx.collections.immutable)
        // Back clears the search first (NavigationBackHandler).
        implementation(libs.jetbrains.navigationevent.compose)
        // Stops the preview when the app goes to the background (LifecycleEventEffect).
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
