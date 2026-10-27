# R8 rules for the release and benchmark builds (P1-15). Background: docs/TECHNICAL_DESIGN.md -> Release builds.
#
# How this file is kept:
# - Most rules come from the libraries themselves. AGP merges every dependency's consumer rules (AAR proguard.txt,
#   META-INF/com.android.tools/r8/*.pro) and the default Android rules (proguard-android-optimize.txt) before this
#   file, so a library that works is not repeated here.
# - Add a rule only when a release run shows a failure (the CI "Release build (R8)" step, or a crash or missing feature
#   in the benchmark build), and write above it which library or class it protects and what failed without it.
# - No blanket keeps (-keep class app.memix.** {*;}) and no blanket -dontwarn: both hide problems and give back the
#   shrinking this build exists for. A -dontwarn names the exact class and the reason.
#
# Checked against the dependency jars and AARs at the versions in gradle/libs.versions.toml (read 27 Oct 2026), so
# nothing is added for them:
# - kotlinx.serialization 1.11.0: keeps `Companion`, `serializer()` and `INSTANCE` of every @Serializable class and
#   object. That covers the Project JSON (Project.serializer() is called directly) and the type-safe navigation
#   routes (app.memix.Routes.kt), which look their serializer up through reflection.
# - Media3 1.11.1 (exoplayer, common): keeps the renderer and media-source constructors it creates by reflection and
#   the effect factories CompositionPlayer needs. Transformer, muxer and container add no rules and need none.
# - Crashlytics 20.1.1: its only rule silences two Android 15/16 classes missing from older SDKs.
# - Koin 4: definitions are lambdas and constructor references written in Kotlin, nothing is looked up by name.
#   (Read from the code, not from Koin's rules.)
# - Compose, Navigation, Lifecycle, Activity, coroutines, Firebase Analytics and Remote Config: nothing added until a
#   release run asks for it.
# - SQLDelight, multiplatform-settings, Compose Resources: generated or direct code, no reflection. Compose Resources
#   live in assets/, which resource shrinking does not touch.
# - Classes named in the manifest (MainActivity, MemixApplication, ExportForegroundService): AGP keeps them.

# Crashlytics: line numbers and file names in crash stack traces. R8 drops both unless they are kept, and the
# uploaded mapping file can only translate what is still there. The default rules above don't keep them.
-keepattributes SourceFile,LineNumberTable
