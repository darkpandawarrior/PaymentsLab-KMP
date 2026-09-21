/**
 * The half of the gateway catalog that cannot reach the browser.
 *
 * `:core:gateway-catalog` carries `wasmJs` so the web preview reads the same hosted-webview list
 * Android and iOS do. `mobile-money` and `wallet` publish android + ios only, so their config lists
 * live here instead — adding them to the wasm-capable module would drop its `wasmJs` target, and
 * the alternative (one module with a hand-built android+ios-but-not-wasm intermediate source set)
 * buys nothing over a second build file.
 *
 * Same Kotlin package as `:core:gateway-catalog` on purpose: the split is a target-capability
 * detail, not something a consumer's import list should have to know about.
 */
plugins {
    id("shared.kmp.library")
}

kotlin {
    android {
        namespace = "com.paymentslab.core.gatewaycatalog.nonweb"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api("com.siddharth.kmp:payments-api:1.0.0")
            api("com.siddharth.kmp:mobile-money:1.0.0")
            api("com.siddharth.kmp:wallet:1.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
