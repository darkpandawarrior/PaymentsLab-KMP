plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    // fun main() lives in Application.kt → file class MainKt.
    mainClass.set("com.paymentslab.backend.MainKt")
}

dependencies {
    // Depend on the shared wire contract (JVM target) so the server serves the EXACT DTOs the client sends.
    implementation(project(":core:protocol"))
    // Wallet ledger wire models (moved to kmp-toolkit alongside WalletLedgerPort) — jvm target lets
    // this pure-JVM Ktor server consume them directly.
    implementation("com.siddharth.kmp:payments-api:1.0.0")
    // minorToDecimalString() — the canonical minor-units formatter, jvm target.
    implementation("com.siddharth.kmp:common:1.0.0")
    // Env-backed credential resolution (PLAB_<GATEWAY>_<MODE>_<KEY>) — shared with the Android app.
    implementation(project(":core:config"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.logback.classic)

    // Outbound HTTP client — for gateways with a real server-side REST API (Paystack).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(25)
}
