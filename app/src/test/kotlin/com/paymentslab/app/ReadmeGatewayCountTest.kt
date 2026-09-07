package com.paymentslab.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The README's "N gateways integrated" claim is hand-written prose (outside `scripts/gen-readme.sh`'s
 * AUTOGEN spans), so nothing regenerates it automatically. This test is the mechanical check instead:
 * it derives the true count from the same config lists [PaymentsLabApplication] wires into the
 * registry, and fails the build if README.md's claim drifts from it.
 *
 * [singleInstanceGatewayCount] is the one number this test can't derive from a list — each of these
 * providers contributes exactly one [com.siddharth.kmp.paymentsapi.PaymentGateway] via its own
 * hand-written Koin module (`upiIntentModule`, `razorpayModule`, `paystackModule`, `flutterwaveModule`,
 * `cashfreeModule`, `stripeModule`, `googlePayModule`, `squareModule`, `omiseModule`, `paytmModule`,
 * `cashModule`, `peachModule`, `nmiModule`, `xenditModule()`, `mpesaModule()` — 15 total). Update this
 * constant in the same change that adds or removes one of those modules in [PaymentsLabApplication].
 */
class ReadmeGatewayCountTest {
    private val singleInstanceGatewayCount = 15

    @Test
    fun readmeGatewayCountMatchesRegistry() {
        val trueCount =
            singleInstanceGatewayCount +
                1 + // wallet
                allMobileMoneyConfigs.size +
                allHostedGatewayConfigs.size +
                allStubGatewayConfigs.size

        val readme = File("../README.md").readText()
        assertTrue(
            "README.md should claim \"$trueCount gateways behind it\" (measured from the registry " +
                "config lists), but that phrase wasn't found. Update the README prose.",
            readme.contains("$trueCount gateways behind it"),
        )
    }
}
