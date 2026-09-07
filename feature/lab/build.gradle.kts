plugins {
    id("shared.cmp.feature")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.paymentslab.feature.lab"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.siddharth.kmp:payments-api:1.0.0")
            implementation(project(":core:orchestration"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:common"))
            implementation(libs.kotlinx.collections.immutable)
            implementation("com.siddharth.kmp:mvi-core:1.0.0")
            // AiSettingsViewModel wraps the toolkit's AiSettingsState (from :designsystem, already
            // pulled in via :core:designsystem's `api`) around ModelManager/OnDeviceLlm (:ai) and
            // SecureKeyStore/ProviderId (:llm-chat).
            implementation("com.siddharth.kmp:ai:1.0.0")
            implementation("com.siddharth.kmp:llm-chat:1.0.0")
            // ErrorExplainer reads AiResult/AiFailure directly (streamRicher's AiChunk.Failed reason) —
            // :ai and :llm-chat only depend on :result as `implementation`, so it isn't visible
            // transitively; every direct consumer of AiFailure/AiResult declares its own dependency
            // (see app/build.gradle.kts's AiModule.kt for the same pattern).
            implementation("com.siddharth.kmp:result:1.0.0")
        }
        androidMain.dependencies {
            implementation(libs.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
