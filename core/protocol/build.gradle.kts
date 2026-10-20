plugins {
    id("shared.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Pin the toolchain to 25 so the JVM target (consumed by the backend server) compiles to Java 25
    // bytecode, matching the backend runtime. Otherwise it defaults higher and the backend test
    // fails with UnsupportedClassVersionError.
    jvmToolchain(25)

    // JVM target so the backend server links against the exact same DTOs the client sends.
    jvm()

    android {
        namespace = "com.paymentslab.core.protocol"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
