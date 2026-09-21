/**
 * The gateway catalog — the plain data lists that say WHICH gateways this product ships.
 *
 * These lists lived in `app/src/main/` and so were Android-only, which is why iOS shipped a
 * hand-written 5-row slice (`ios/shared/.../IosGatewayConfigs.kt`) and the web preview a 13-row one
 * (`web/.../WebGatewayConfigs.kt`) against Android's 44 hosted rows. They have zero `android.*` /
 * `androidx.*` imports — nothing about a `HostedGatewayConfig` is platform-specific — so commonMain
 * is where they belong, and all three entry points now read the same list.
 *
 * `wasmJs` is declared here because every dependency below publishes one. The two lists whose
 * provider modules do NOT (`mobile-money`, `wallet`) live in `:core:gateway-catalog-nonweb`.
 *
 * Deliberately NOT folded into `:core:config` (which carries a `jvm()` target for the backend):
 * `hosted-webview` publishes no jvm target, so that would break the backend's view of it.
 */
plugins {
    id("shared.kmp.library")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.paymentslab.core.gatewaycatalog"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            // Every config type below is on this module's public surface, so `api` not
            // `implementation` — consumers name `HostedGatewayConfig` / `StubGatewayConfig` in
            // their own code, and `stubGatewayModule()` returns a Koin `Module`.
            api("com.siddharth.kmp:payments-api:1.0.0")
            api("com.siddharth.kmp:hosted-webview:1.0.0")
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
