plugins {
    // Compose for the two system screens the app opens and gets results from: the photo picker and the storage manager.
    id("memix.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.koin.android)
        implementation(libs.androidx.activity.compose)
        implementation(project.dependencies.platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
        implementation(libs.firebase.config)
    }
}
