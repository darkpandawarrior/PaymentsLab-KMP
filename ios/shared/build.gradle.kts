/**
 * B8: the iOS app's shared surface. Deliberately its own module rather than reusing `feature:lab`
 * directly — AGP 9's KMP library plugin can't also be `com.android.application`/host a
 * `.framework` export (see `kmp-boundaries` skill), so the "package everything for Xcode" concern
 * lives here, separate from the feature modules it aggregates.
 *
 * Gateway scope now matches the Android app's config-driven catalog: `:core:gateway-catalog` is
 * commonMain, so hosted-webview (archetype C), mobile-money (D), stub and wallet rows are read from
 * the one list both apps share. What remains Android-only is the archetype-A native-SDK set whose
 * vendor SDK has no iOS build at all (GooglePay), or whose iOS SDK is wired through a Swift
 * `*CheckoutHost` here instead (Stripe/Razorpay/Cashfree/Square/Omise).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "PaymentsLabShared"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation("com.siddharth.kmp:payments-api:1.0.0")
            implementation(project(":core:protocol"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:data"))
            implementation(project(":core:orchestration"))
            implementation(project(":core:designsystem"))
            // The shared gateway catalog — the same 55 config-driven rows the Android app reads,
            // instead of the 5-row hand-maintained slice this module used to carry.
            implementation(project(":core:gateway-catalog"))
            implementation("com.siddharth.kmp:hosted-webview:1.0.0")
            implementation("com.siddharth.kmp:mobile-money:1.0.0")
            implementation("com.siddharth.kmp:wallet:1.0.0")
            implementation(project(":feature:lab"))
            implementation(project(":feature:checkout-demo"))
            implementation(project(":feature:history"))
            implementation(project(":feature:home"))

            implementation(libs.koin.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
