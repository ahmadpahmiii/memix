plugins {
    id("memix.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.model)
        api(libs.kotlinx.coroutines.core)
    }
}
