plugins {
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.designsystem)
        implementation(projects.core.model)
    }
}
