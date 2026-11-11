plugins {
    id("memix.kmp.library")
    alias(libs.plugins.sqldelight)
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.sqldelight.coroutines)
        api(libs.multiplatform.settings)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
        // The catalog's remote side (P3-01): supabase-kt reads the tables, Ktor fetches the waveform files.
        implementation(project.dependencies.platform(libs.supabase.bom))
        implementation(libs.supabase.postgrest)
        implementation(libs.ktor.client.core)
    }
    // Ktor needs an engine per platform; supabase-kt and the waveform client both pick it up from the classpath.
    sourceSets.androidMain.dependencies {
        implementation(libs.ktor.client.okhttp)
    }
    sourceSets.iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
    }
}

sqldelight {
    databases {
        // Uses SQLDelight's default SQLite 3.18 dialect on purpose: Android 10 (minSdk 29) ships SQLite 3.22,
        // so newer syntax such as UPSERT (3.24) would fail on those phones.
        create("MemixDatabase") {
            packageName.set("app.memix.core.data.db")
            // 1.db is the version-1 schema; verifySqlDelightMigration checks that 1.db plus every .sqm equals the .sq files.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
