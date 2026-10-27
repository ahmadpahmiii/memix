plugins {
    id("memix.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets.commonMain.dependencies {
        // api: the generated serializers (Project.serializer()) are part of this module's API.
        api(libs.kotlinx.serialization.core)
    }
}
