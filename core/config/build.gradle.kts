plugins {
    id("shared.kmp.library")
}

kotlin {
    // Toolchain 25 so the JVM target compiles to Java 25 bytecode (see core:protocol for rationale).
    jvmToolchain(25)

    // JVM target so the backend can resolve credentials from System.getenv with the same logic.
    jvm()

    android {
        namespace = "com.paymentslab.core.config"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api("com.siddharth.kmp:payments-api:1.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
