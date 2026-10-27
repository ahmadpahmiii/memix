import java.time.Duration
import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

/**
 * The release version code (P1-15): whole minutes since 1 Jan 2026 UTC. Every build the owner makes is higher than the
 * one before, whatever happens to git (a squash merge would lower a commit count), and it stays far below Play's limit of
 * 2,100,000,000. A ValueSource so the configuration cache asks the clock again for each release build instead of
 * freezing the first number it saw; builds that don't touch the release variant never read it.
 */
abstract class ReleaseVersionCodeSource : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val firstMinute = Instant.parse("2026-01-01T00:00:00Z")
        return Duration.between(firstMinute, Instant.now()).toMinutes().toInt()
    }
}

val releaseVersionCode = providers.of(ReleaseVersionCodeSource::class) {}

android {
    namespace = "app.memix.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.memix"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Debug and benchmark builds are never uploaded, so they keep 1 (and Gradle's configuration cache); the release
        // variant gets its number from releaseVersionCode below. versionName is bumped by hand at each phase end.
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        // The build the owner signs in Android Studio and uploads to Play (P1-15). R8 shrinks and renames the code and drops
        // unused resources; the rules are in proguard-rules.pro. No signingConfig here: the keystore stays on the owner's
        // Mac (docs/CREDENTIALS.md), so this variant builds unsigned in CI.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // For measuring speed on a phone (P1-04): the release code, R8 included (it copies the release block above, so
        // it must stay below it), signed with the debug key so it installs from a laptop over a debug install (keeping
        // its data), profileable from the shell (src/benchmark), and with the hand-check hooks on
        // (res/values/hand_checks.xml). Never uploaded to Play.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    // Play would send a phone only the languages in its system settings, but Android 13+ lets people pick another
    // language for one app (res/xml/locales_config.xml). Keeping every language in the base split keeps the export
    // notification's strings (res/values-xx in :platform:services) in that language too.
    bundle {
        language {
            enableSplit = false
        }
    }
    lint {
        checkDependencies = true // the KMP library modules have no lint task of their own
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output -> output.versionCode.set(releaseVersionCode) }
    }
}

// Crashlytics adds uploadCrashlyticsMappingFile<Variant> to every minified variant and runs it right after R8. The
// upload fails the build when it can't reach Firebase, so it is limited to what is useful:
//   release (default): the owner's Play build, with the Mac online.
//   all: also benchmark, for the one-time check that a crash is deobfuscated (docs/TECHNICAL_DESIGN.md → Release builds).
//   none: CI, whose google-services.json is a placeholder.
// Pass it as -Pmemix.mappingUpload=<release|all|none>.
val mappingUploadTaskPrefix = "uploadCrashlyticsMappingFile"
val mappingUploadVariants: Set<String> = when (val choice = providers.gradleProperty("memix.mappingUpload").getOrElse("release")) {
    "release" -> setOf("Release")
    "all" -> setOf("Release", "Benchmark")
    "none" -> emptySet()
    else -> throw GradleException("memix.mappingUpload must be release, all or none, not '$choice'.")
}
tasks.configureEach {
    val variant = name.removePrefix(mappingUploadTaskPrefix)
    val isMappingUpload = variant != name
    if (isMappingUpload && variant !in mappingUploadVariants) enabled = false
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    // Reports leaked screens, ViewModels and views on the phone in debug builds; nothing ships in release.
    debugImplementation(libs.leakcanary.android)
}
