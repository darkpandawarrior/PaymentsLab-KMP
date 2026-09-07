import java.io.FileInputStream
import java.util.Properties

plugins {
    id("shared.android.application")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.roborazzi)
}

// Release signing, reads from keystore.properties (gitignored) or env vars (CI). Falls back to
// debug signing if neither is present, so `assembleRelease` still succeeds locally and in CI
// without secrets configured.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            FileInputStream(keystorePropertiesFile).use { load(it) }
        }
    }
val hasReleaseSigning =
    keystorePropertiesFile.exists() || System.getenv("RELEASE_STORE_FILE") != null

// F-Droid reproducible build flag (`./gradlew :app:assembleRelease -Pfdroid`). Disables R8/resource
// shrinking, which isn't bit-for-bit reproducible across machines.
val fdroidBuild = providers.gradleProperty("fdroid").isPresent

// Locks the app's runtime dependency graph. A silent transitive version bump fails
// `./gradlew :app:dependencyGuard`; re-baseline intentionally with `:app:dependencyGuardBaseline`.
dependencyGuard {
    configuration("debugRuntimeClasspath")
}

// Three-tier versioning (MARKETING/BUILDCODE/FINGERPRINT) — single source of truth is
// scripts/version.sh, which derives all three from the repo-root VERSION/BUILD_NUMBER/MILESTONE
// files + live git state. Shared with CI's github-release.yml iOS job and scripts/bump_version.sh
// so nothing is ever hand-typed twice. See docs/RELEASE.md.
val versionStamp: Map<String, String> =
    providers
        .exec {
            commandLine(rootProject.file("scripts/version.sh").absolutePath)
        }.standardOutput.asText.get()
        .lineSequence()
        .filter { it.contains('=') }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

fun readVersionName(): String = versionStamp.getValue("MARKETING")

fun readFingerprint(): String = versionStamp.getValue("FINGERPRINT")

val appVersionName = readVersionName()
val appBuildNumber = versionStamp.getValue("BUILDCODE").toInt()
val appFingerprint = readFingerprint()

android {
    namespace = "com.paymentslab.app"

    // The shared `shared.android.application` convention plugin defaults `buildConfig = false`
    // (matches AGP 9's own default). This app relies on BuildConfig — PaymentsLabApplication reads
    // BuildConfig.BUILD_TYPE/VERSION_NAME, security/network read BACKEND_URL + BYPASS_* — so it must
    // opt back in explicitly. Without this the buildConfigField(...) calls below don't compile.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.paymentslab.app"
        // Bumped 24 -> 26 for this lane: :ai's on-device LLM tier (ML Kit GenAI / MediaPipe) ships
        // an AndroidManifest with minSdkVersion 26 and the manifest merger hard-fails an app below
        // that (HireSignal, the other family app on this AI stack, is minSdk 26 for the same
        // reason). A real device-support change, not a version bump — see the PR body.
        minSdk = 26
        targetSdk = 36
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") } // drop emulator-only x86/x86_64
        versionCode = appBuildNumber
        versionName = appVersionName
        buildConfigField("String", "FINGERPRINT", "\"$appFingerprint\"")

        // VAPT bypass flags (common VAPT-testing bypass pattern). Default false = full protection; a
        // dedicated VAPT/compliance test variant flips them so pentesters can run on a rooted/hooked
        // device without the app hard-blocking itself. Never true in a real release.
        buildConfigField("boolean", "BYPASS_ROOT", "false")
        buildConfigField("boolean", "BYPASS_HOOK", "false")
        buildConfigField("boolean", "BYPASS_SSL", "false")
        buildConfigField("boolean", "BYPASS_DEBUGGER", "false")

        // Per-environment backend URL. Debug/vapt talk to the local dev server (emulator loopback);
        // release points at the real host. The app feeds this into networkModule(PaymentApiConfig(...)).
        buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:8080\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile =
                    file(
                        keystoreProperties.getProperty("storeFile")
                            ?: System.getenv("RELEASE_STORE_FILE"),
                    )
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                        ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                        ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
                        ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug installs need to be told apart at a glance (multiple CI builds a day); release
            // keeps the bare MARKETING versionName for store listings.
            versionNameSuffix = "-$appFingerprint"
        }

        release {
            // The -Pfdroid flag disabled R8 so F-Droid's build server could rebuild from
            // source and byte-compare. That never applied to PaymentsLab-KMP: it bundles the
            // Razorpay, Cashfree, Stripe and Play Wallet SDKs, which are the point of the
            // app, so it is permanently ineligible for official fdroiddata and reaches
            // F-Droid only as a prebuilt Binaries entry nobody re-builds.
            //
            // Re-applied after the kmp-toolkit bump in d57cbc0 silently reverted it: that
            // branch was cut from an older main and carried this file along, so merging it
            // undid a landed fix. The published APK went 32.6MB back to 39.3MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "BACKEND_URL", "\"https://api.paymentslab.example\"")
            signingConfig =
                if (hasReleaseSigning) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }

        // VAPT / pen-test variant: a debuggable build that flips the security BYPASS_* flags so a
        // tester can run on a rooted/hooked/proxied device without the app hard-blocking itself.
        // Installs alongside via the .vapt applicationId suffix. Never distributed.
        create("vapt") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug" // library modules only define debug/release
            applicationIdSuffix = ".vapt"
            versionNameSuffix = "-vapt"
            buildConfigField("boolean", "BYPASS_ROOT", "true")
            buildConfigField("boolean", "BYPASS_HOOK", "true")
            buildConfigField("boolean", "BYPASS_SSL", "true")
            buildConfigField("boolean", "BYPASS_DEBUGGER", "true")
        }
    }

    testOptions {
        unitTests {
            // Roborazzi/Robolectric render real Compose UI on the JVM — needs Android resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core
    implementation("com.siddharth.kmp:payments-api:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:orchestration"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    // Providers
    implementation("com.siddharth.kmp:upi-intent:1.0.0")
    implementation("com.siddharth.kmp:razorpay:1.0.0")
    implementation("com.siddharth.kmp:paystack:1.0.0")
    implementation("com.siddharth.kmp:flutterwave:1.0.0")
    implementation("com.siddharth.kmp:cashfree:1.0.0")
    implementation("com.siddharth.kmp:stripe:1.0.0")
    implementation("com.siddharth.kmp:hosted-webview:1.0.0")
    implementation("com.siddharth.kmp:stripe-connect:1.0.0")
    implementation("com.siddharth.kmp:googlepay:1.0.0")
    implementation("com.siddharth.kmp:mobile-money:1.0.0")
    implementation("com.siddharth.kmp:square:1.0.0")
    implementation("com.siddharth.kmp:omise:1.0.0")
    implementation("com.siddharth.kmp:wallet:1.0.0")
    implementation("com.siddharth.kmp:paytm:1.0.0")
    implementation("com.siddharth.kmp:cash:1.0.0")
    implementation("com.siddharth.kmp:xendit:1.0.0")
    implementation("com.siddharth.kmp:mpesa:1.0.0")
    implementation("com.siddharth.kmp:peach:1.0.0")
    implementation("com.siddharth.kmp:nmi:1.0.0")

    // Features
    implementation(project(":feature:lab"))
    implementation(project(":feature:history"))
    implementation(project(":feature:checkout-demo"))
    implementation(project(":feature:home"))

    // Compose (androidx BOM) + navigation
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.jb.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.napier)

    // WorkManager — background reconciliation of pending payments (process-death recovery)
    implementation(libs.workmanager.runtime)

    // Security suite (Keystore store, FLAG_SECURE, device-integrity, pinning config)
    implementation("com.siddharth.kmp:security:1.0.0")

    // AI seam: on-device LLM tier + cloud BYOK provider chain (AiModule.kt). :result is `implementation`
    // inside :ai/:llm-chat themselves, so AiFailure/AiResult aren't visible transitively — declared
    // directly here since NoBackendAiProvider names them in its own signature.
    implementation("com.siddharth.kmp:ai:1.0.0")
    implementation("com.siddharth.kmp:llm-chat:1.0.0")
    implementation("com.siddharth.kmp:result:1.0.0")

    // Provider SDKs referenced directly by MainActivity's Activity-level callback wiring
    // (providers depend on these via `implementation`, so the types aren't transitive here).
    implementation(libs.razorpay.checkout)
    implementation(libs.stripe.paymentsheet)
    implementation(libs.cashfree.pg.api)
    implementation(libs.cashfree.pg.ui)
    implementation("com.squareup.sdk.in-app-payments:card-entry:1.6.8")

    // Roborazzi screenshot tests (JVM/Robolectric — no emulator). Snapshot the design system.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.collections.immutable)
    debugImplementation(libs.compose.ui.test.manifest)
}
