plugins {
    id("memix.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
    }
}
