plugins {
    id("shared.kmp.compose")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.paymentslab.core.designsystem"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.siddharth.kmp:payments-api:1.0.0")
            // StepTimeline/TimelineStep/StepState/PayloadCard/RedactionReveal/AnimatedCounter now
            // live in :designsystem (backlog #30/#31); StepMapper.kt's public toTimelineStep()
            // returns TimelineStep, so this is `api`, matching HireSignal's precedent for the
            // same coordinate.
            api("com.siddharth.kmp:designsystem:1.0.0")
            implementation(project(":core:common"))
            implementation(libs.runtime)
            implementation(libs.ui)
            implementation(libs.material3)
            implementation(libs.foundation)
            implementation(libs.material.icons.extended)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview.mp)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The preview RENDERER. `libs.ui.tooling.preview.mp` above gives commonMain the @Preview
// ANNOTATION; without this artifact on the Android runtime classpath the IDE has nothing to draw
// them with. Two dependencies, not one — that split is the usual reason previews render nothing.
//
// `androidRuntimeClasspath` is the configuration the AGP KMP library plugin
// (com.android.kotlin.multiplatform.library, applied by `shared.kmp.compose`) exposes. :app is a
// plain Android application module and already has the AndroidX equivalent via
// `debugImplementation(libs.compose.ui.tooling)` — different configuration, different artifact
// group, same job. Declared per-module rather than in the shared convention plugin, which is a
// submodule every repo in the family consumes.
dependencies {
    "androidRuntimeClasspath"(libs.ui.tooling.mp)
}
