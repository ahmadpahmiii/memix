plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.designsystem)
        api(libs.jetbrains.lifecycle.viewmodel)
        api(libs.kotlinx.coroutines.core)
        implementation(projects.core.model)
    }
}
