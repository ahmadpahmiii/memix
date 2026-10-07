plugins {
    id("memix.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.domain)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.sqldelight.coroutines)
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
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
