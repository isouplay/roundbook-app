import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Local core of the app: event log, storage (SQLDelight), places, menu versions, evenings, entries, favourites.
// Later: calculations (statistics, blood alcohol, bill). No UI.
// The JVM target exists for fast tests without a device.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        // SQLite of the system (the SQLDelight plugin links it too; stated here for the test binaries).
        iosTarget.binaries.all {
            linkerOpts("-lsqlite3")
        }
    }

    jvm()

    android {
        namespace = "io.github.isouplay.roundbook.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.sqldelight.runtime)
            // Reading as Flow (lists update themselves) and writing off the main thread.
            api(libs.sqldelight.coroutines)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        named("androidHostTest") {
            dependencies {
                // Android host tests run on the JVM: same in-memory SQLite driver as the JVM tests.
                implementation(libs.sqldelight.sqliteDriver)
            }
        }
    }
}

sqldelight {
    databases {
        create("RoundbookDatabase") {
            packageName.set("io.github.isouplay.roundbook.domain.db")
            // Schema files (1.db, 2.db …) for checking migrations, created with
            // generateCommonMainRoundbookDatabaseSchema once there is a first migration.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
