plugins {
    `kotlin-dsl`
}

group = "com.paymentslab.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // compileOnly avoids ClassCastException when the same plugin class is loaded by two
    // classloaders at different versions.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        // kmpLibrary / kmpCompose / cmpFeature / androidApplication / test are now provided by the
        // shared `external/kmp-build-logic` composite build (plugin ids `shared.*`). Only the two
        // PaymentsLab-KMP-specific plugins that are out of the shared repo's scope stay registered here:
        // androidProvider (Android-only gateway SDK modules) and androidLibrary (core:security).
        register("androidProvider") {
            id = "paymentslab.android.provider"
            implementationClass = "AndroidProviderConventionPlugin"
        }
        register("androidLibrary") {
            id = "paymentslab.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
