package com.paymentslab.ios.shared

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.ReturnUrlMatchers
import com.siddharth.kmp.provider.mobilemoney.MobileMoneyConfig

/**
 * The iOS app's gateway list — a deliberately small slice of the Android app's ~65-row catalog,
 * proving archetype C (hosted-webview) and D (mobile-money) genuinely run cross-platform rather
 * than wiring every gateway again. Same MOCK_MODE-by-default honesty as the Android configs (see
 * `app/HostedGatewayConfigs.kt`) — these are intentionally identical in shape, just fewer of them.
 */
val iosHostedGatewayConfigs: List<HostedGatewayConfig> =
    listOf(
        HostedGatewayConfig(
            gatewayId = GatewayId("paystack"),
            displayName = "Paystack",
            region = "Africa",
            docsPath = "docs/providers/paystack.md",
            blurb = "Hosted checkout via Paystack's Standard Checkout — real when sandbox keys are set.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            status = GatewayStatus.MOCK_MODE,
            buildCheckoutUrl = { params -> params["checkout_url"].orEmpty() },
            matchReturn =
                ReturnUrlMatchers.byMarker(
                    successMarker = "/mock/return/success",
                    failureMarker = "/mock/return/failure",
                ),
        ),
        HostedGatewayConfig(
            gatewayId = GatewayId("paypal"),
            displayName = "PayPal",
            region = "Global",
            docsPath = "docs/providers/paypal.md",
            blurb = "Real Orders v2 REST API when sandbox credentials are configured.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            status = GatewayStatus.MOCK_MODE,
            buildCheckoutUrl = { params -> params["checkout_url"].orEmpty() },
            matchReturn =
                ReturnUrlMatchers.byMarker(
                    successMarker = "/mock/return/success",
                    failureMarker = "/mock/return/failure",
                ),
        ),
        HostedGatewayConfig(
            gatewayId = GatewayId("midtrans"),
            displayName = "Midtrans",
            region = "Indonesia",
            docsPath = "docs/providers/midtrans.md",
            blurb = "Snap Checkout — deliberately not the native SDK, which Midtrans is sunsetting Jun 2026.",
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS),
            status = GatewayStatus.MOCK_MODE,
            buildCheckoutUrl = { params -> params["checkout_url"].orEmpty() },
            matchReturn =
                ReturnUrlMatchers.byMarker(
                    successMarker = "/mock/return/success",
                    failureMarker = "/mock/return/failure",
                ),
        ),
    )

val iosMobileMoneyConfigs: List<MobileMoneyConfig> =
    listOf(
        MobileMoneyConfig(
            gatewayId = GatewayId("mpesa"),
            displayName = "M-Pesa",
            region = "Kenya/Tanzania",
            docsPath = "docs/providers/mpesa.md",
            blurb = "Async mobile money — confirmation happens on the payer's phone, no in-app SDK/UI.",
        ),
        MobileMoneyConfig(
            gatewayId = GatewayId("mtnmomo"),
            displayName = "MTN MoMo",
            region = "Africa",
            docsPath = "docs/providers/mtnmomo.md",
            blurb = "Async mobile money — confirmation happens on the payer's phone, no in-app SDK/UI.",
        ),
    )
