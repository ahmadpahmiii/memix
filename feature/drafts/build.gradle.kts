plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        implementation(libs.kotlinx.collections.immutable)
        // Shown and hidden with the screen (LifecycleEventEffect), and the copy sheet's progress (collectAsStateWithLifecycle).
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
