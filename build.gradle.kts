// Loads build-logic's classpath (AGP, Kotlin, Compose) once at the root, so modules apply those plugins by id.
plugins {
    id("memix.kmp.library") apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
