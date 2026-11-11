import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Properties

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

// Build-time values from local.properties (not committed), or empty. Read as a file provider so the configuration cache
// notices when the file changes. memix.media.baseUrl is the fallback for Remote Config's media_base_url (P3-13);
// memix.supabase.url and memix.supabase.publishableKey say where the catalog lives (P3-01, docs/CREDENTIALS.md).
fun localProperty(name: String): Provider<String> =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty(name).orEmpty().trim() }
        .orElse("")

// The catalog settings are checked at build time (P3-01), because a build that lacks them runs but shows only the saved
// catalog, and a build that holds a secret key would ship it.
//   - Every variant: memix.supabase.publishableKey must be a publishable key ("sb_publishable_...") or a JWT whose role is
//     "anon". A "sb_secret_..." key or a "service_role" JWT fails the build.
//   - The release variant also needs memix.supabase.url and the key to be set, unless -Pmemix.catalogConfig=none is passed
//     (CI's setting: it only checks R8 and uploads nothing). The owner's Play bundle is built without the flag.
val supabaseUrlSetting = localProperty("memix.supabase.url")
val supabaseKeySetting = localProperty("memix.supabase.publishableKey")
val catalogConfigNone = providers.gradleProperty("memix.catalogConfig").map { choice ->
    if (choice != "none") throw GradleException("memix.catalogConfig must be none, not '$choice'.")
    true
}.orElse(false)

val checkSupabaseKeyIsPublic = tasks.register("checkSupabaseKeyIsPublic") {
    val key = supabaseKeySetting
    doLast {
        val value = key.get()
        // A JWT key carries its role in the payload (the middle part, base64url JSON).
        val jwtRole = value.split('.').getOrNull(1)
            ?.let { payload -> runCatching { String(Base64.getUrlDecoder().decode(payload)) }.getOrNull() }
            ?.let { json -> Regex("\"role\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) }
        val isPublishable = value.startsWith("sb_publishable_") || (value.startsWith("eyJ") && jwtRole == "anon")
        if (value.isNotEmpty() && !isPublishable) {
            throw GradleException(
                "memix.supabase.publishableKey in local.properties is not a publishable key (sb_publishable_... or an anon JWT). " +
                    "A secret or service_role key must never go into the app. See docs/CREDENTIALS.md.",
            )
        }
    }
}
val checkCatalogConfigIsSet = tasks.register("checkCatalogConfigIsSet") {
    val url = supabaseUrlSetting
    val key = supabaseKeySetting
    val skip = catalogConfigNone
    doLast {
        if (!skip.get() && (url.get().isEmpty() || key.get().isEmpty())) {
            throw GradleException(
                "The release build has no catalog settings: set memix.supabase.url and memix.supabase.publishableKey in " +
                    "local.properties, or pass -Pmemix.catalogConfig=none for a build that is never uploaded (CI).",
            )
        }
    }
}
tasks.configureEach {
    if (name == "preBuild") dependsOn(checkSupabaseKeyIsPublic)
    if (name == "preReleaseBuild") dependsOn(checkCatalogConfigIsSet)
}

androidComponents {
    onVariants { variant ->
        variant.manifestPlaceholders.put("mediaBaseUrl", localProperty("memix.media.baseUrl"))
        variant.manifestPlaceholders.put("supabaseUrl", localProperty("memix.supabase.url"))
        variant.manifestPlaceholders.put("supabasePublishableKey", localProperty("memix.supabase.publishableKey"))
    }
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
