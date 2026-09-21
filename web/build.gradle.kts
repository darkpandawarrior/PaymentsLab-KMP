/**
 * Browser preview shell (Kursi `cmp-web` pattern): a wasmJs executable that runs the gateway
 * catalog + explained-checkout demo entirely in MOCK_MODE — in-memory backend/journal fakes, the
 * real orchestrator FSM and hosted-webview archetype underneath. `wasmJsBrowserDistribution`
 * output is what cv-siddharth embeds as `public/paymentslab-app/`.
 *
 * Deliberately narrower than `:ios:shared`: only the wasm-capable slice (no Room-backed history,
 * no native-SDK archetype-A providers — those don't exist on web by construction).
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation("com.siddharth.kmp:payments-api:1.0.0")
            implementation("com.siddharth.kmp:hosted-webview:1.0.0")
            implementation("com.siddharth.kmp:mvi-core:1.0.0")
            implementation(project(":core:common"))
            // The shared hosted-webview catalog — the same 44 rows Android and iOS read, instead of
            // the 13-row hand-maintained slice this module used to carry.
            implementation(project(":core:gateway-catalog"))
            implementation(project(":core:orchestration"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:lab"))
            implementation(project(":feature:checkout-demo"))

            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
        }
    }
}
