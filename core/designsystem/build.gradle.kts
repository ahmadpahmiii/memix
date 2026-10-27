plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.jetbrains.navigationevent.compose)
        // ToolBar takes an ImmutableList, so callers see the type too.
        api(libs.kotlinx.collections.immutable)
    }
}
