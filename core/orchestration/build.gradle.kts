plugins {
    id("shared.kmp.library")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.paymentslab.core.orchestration"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api("com.siddharth.kmp:payments-api:1.0.0")
            implementation(project(":core:common"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
