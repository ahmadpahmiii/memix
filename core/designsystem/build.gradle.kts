plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.jetbrains.navigationevent.compose)
    }
}
