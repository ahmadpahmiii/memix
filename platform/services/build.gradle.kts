plugins {
    id("memix.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
}
