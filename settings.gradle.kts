rootProject.name = "Memix"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":composeApp")
include(":core:model", ":core:domain", ":core:data", ":core:designsystem", ":core:ui")
include(":engine:video", ":engine:photo", ":engine:segmentation")
include(":platform:services")
include(
    ":feature:onboarding",
    ":feature:home",
    ":feature:templates",
    ":feature:sounds",
    ":feature:drafts",
    ":feature:video-editor",
    ":feature:photo-editor",
    ":feature:export",
)
