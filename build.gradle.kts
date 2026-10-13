plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Check commands. Same on Windows, Linux and GitHub Actions.
// iOS tests need macOS and run in GitHub Actions (.github/workflows/verify.yml).

tasks.register("verifyQuick") {
    group = "verification"
    description = "Fast tests without device or emulator: :core and :app:domain on the JVM, plus :server."
    dependsOn(
        ":core:jvmTest",
        ":app:domain:jvmTest",
        ":server:test",
    )
}

tasks.register("verifyAll") {
    group = "verification"
    description = "All tests possible on this machine plus the Android debug build."
    dependsOn(
        "verifyQuick",
        ":core:testAndroidHostTest",
        ":app:domain:testAndroidHostTest",
        ":app:shared:testAndroidHostTest",
        ":app:androidApp:assembleDebug",
    )
}
