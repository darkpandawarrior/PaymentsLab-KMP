plugins {
    id("shared.cmp.feature")
}

kotlin {
    android {
        namespace = "com.paymentslab.feature.history"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
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
