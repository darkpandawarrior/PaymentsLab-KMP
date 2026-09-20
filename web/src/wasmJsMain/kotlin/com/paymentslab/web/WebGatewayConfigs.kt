package com.paymentslab.web

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.ReturnUrlMatchers

/**
 * The web preview's gateway list — a representative regional slice of the Android app's ~46-row
 * hosted catalog (`core/gateway-catalog/.../HostedGatewayConfigs.kt`), following the per-entry-point-config precedent
 * set by `ios/shared/IosGatewayConfigs.kt`. Everything is MOCK_MODE by construction: the browser
 * build has no backend and no sandbox keys, so every run is the simulated end-to-end lifecycle.
 */
private fun webHostedGatewayConfig(
    id: String,
    displayName: String,
    region: String,
    blurb: String,
    capabilities: Set<Capability> = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
) = HostedGatewayConfig(
    gatewayId = GatewayId(id),
    displayName = displayName,
    region = region,
    docsPath = "docs/providers/$id.md",
    blurb = blurb,
    capabilities = capabilities,
    status = GatewayStatus.MOCK_MODE,
    buildCheckoutUrl = { params -> params["checkout_url"].orEmpty() },
    matchReturn =
        ReturnUrlMatchers.byMarker(
            successMarker = "/mock/return/success",
            failureMarker = "/mock/return/failure",
        ),
)

val webHostedGatewayConfigs: List<HostedGatewayConfig> =
    listOf(
        webHostedGatewayConfig(
            id = "paystack",
            displayName = "Paystack",
            region = "Africa",
            blurb = "Hosted checkout via Paystack's Standard Checkout — real when sandbox keys are set.",
        ),
        webHostedGatewayConfig(
            id = "paypal",
            displayName = "PayPal",
            region = "Global",
            blurb = "Real Orders v2 REST API when sandbox credentials are configured.",
        ),
        webHostedGatewayConfig(
            id = "midtrans",
            displayName = "Midtrans",
            region = "Indonesia",
            blurb = "Snap Checkout — deliberately not the native SDK, which Midtrans is sunsetting Jun 2026.",
        ),
        webHostedGatewayConfig(
            id = "mollie",
            displayName = "Mollie",
            region = "EU",
            blurb = "Hosted checkout via Mollie's Payments API — needs a registered business account for test keys.",
        ),
        webHostedGatewayConfig(
            id = "payu",
            displayName = "PayU",
            region = "India/LatAm",
            blurb = "Hosted checkout across PayU's India and LatAm rails.",
        ),
        webHostedGatewayConfig(
            id = "worldpay",
            displayName = "Worldpay",
            region = "Global",
            blurb = "Hosted Payment Pages — enterprise onboarding gates the real sandbox.",
        ),
        webHostedGatewayConfig(
            id = "conekta",
            displayName = "Conekta",
            region = "Mexico",
            blurb = "Hosted checkout with OXXO cash vouchers alongside cards.",
        ),
        webHostedGatewayConfig(
            id = "sslcommerz",
            displayName = "SSLCommerz",
            region = "Bangladesh",
            blurb = "Bangladesh's largest aggregator — hosted checkout over cards, MFS and net banking.",
        ),
        webHostedGatewayConfig(
            id = "bkash",
            displayName = "bKash",
            region = "Bangladesh",
            blurb = "Mobile-wallet checkout — tokenized hosted flow.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
        ),
        webHostedGatewayConfig(
            id = "ozow",
            displayName = "Ozow",
            region = "South Africa",
            blurb = "Instant EFT — bank-redirect hosted flow, no cards involved.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
        ),
        webHostedGatewayConfig(
            id = "twint",
            displayName = "TWINT",
            region = "Switzerland",
            blurb = "Switzerland's dominant mobile wallet — QR/app-switch hosted flow.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
        ),
        webHostedGatewayConfig(
            id = "phonepe",
            displayName = "PhonePe",
            region = "India",
            blurb = "PhonePe PG hosted checkout — UPI-first with cards fallback.",
        ),
    )
