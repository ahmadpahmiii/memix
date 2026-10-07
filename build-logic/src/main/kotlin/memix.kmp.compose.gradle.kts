plugins {
    id("memix.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = versionCatalogs.named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

kotlin {
    android {
        androidResources { enable = true }
    }
    sourceSets.commonMain.dependencies {
        implementation(lib("compose-runtime"))
        implementation(lib("compose-foundation"))
        implementation(lib("compose-ui"))
        implementation(lib("compose-components-resources"))
    }
}
