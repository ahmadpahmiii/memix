plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(projects.core.ui)
        implementation(libs.kotlinx.collections.immutable)
        // System back steps through the screens (Added, builder, packs), and the state is collected with the lifecycle.
        implementation(libs.jetbrains.navigationevent.compose)
        implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
}
