/**
 * The gateway catalog — the plain data lists that say WHICH gateways this product ships.
 *
 * These four files lived in `app/src/main/` and so were Android-only, which is why iOS shipped a
 * hand-written 5-row slice (`ios/shared/.../IosGatewayConfigs.kt`) against Android's ~55. They have
 * zero `android.*` / `androidx.*` imports — nothing about a `HostedGatewayConfig` or a
 * `MobileMoneyConfig` is platform-specific — so commonMain is where they belong, and both apps now
 * read the same list.
 *
 * Deliberately NOT folded into `:core:config` (which carries a `jvm()` target for the backend) or
 * `:core:orchestration` (which carries `wasmJs`): the `mobile-money` and `wallet` providers publish
 * android + ios only, so either would break that module's extra target.
 */
plugins {
    id("shared.kmp.library")
}

kotlin {
    android {
        namespace = "com.paymentslab.core.gatewaycatalog"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            // Every config type below is on this module's public surface, so `api` not
            // `implementation` — consumers name `HostedGatewayConfig`/`MobileMoneyConfig`/
            // `WalletConfig`/`StubGatewayConfig` in their own code.
            api("com.siddharth.kmp:payments-api:1.0.0")
            api("com.siddharth.kmp:hosted-webview:1.0.0")
            api("com.siddharth.kmp:mobile-money:1.0.0")
            api("com.siddharth.kmp:wallet:1.0.0")
            // stubGatewayModule() returns a Koin Module — also public surface.
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
