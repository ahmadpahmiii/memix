plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        // The Exporting screen takes system back while it shows ("Stop exporting?").
        implementation(libs.jetbrains.navigationevent.compose)
        // The result stops playing when the app stops (collectAsStateWithLifecycle, LifecycleEventEffect).
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
