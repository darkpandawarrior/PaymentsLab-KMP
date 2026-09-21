package com.paymentslab.ios.shared

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.ReturnUrlMatchers
import com.siddharth.kmp.provider.mobilemoney.MobileMoneyConfig

/**
 * The two gateways iOS serves from the GENERIC archetypes that Android serves from a dedicated
 * native provider module instead — so they are absent from `:core:gateway-catalog`'s shared lists
 * and would otherwise be silently dropped from iOS.
 *
 * Everything else (44 hosted, 7 mobile-money, 3 stub, 1 wallet) now comes from the shared catalog,
 * which is the whole point: `IosGatewayConfigs.kt` used to hand-maintain a 5-row slice of Android's
 * catalog. See `core/gateway-catalog/`.
 *
 *  - `paystack` — Android runs `provider:paystack` (real Standard Checkout REST); there is no iOS
 *    binding for that module, so iOS falls back to the generic hosted-webview archetype.
 *  - `mpesa`    — Android runs `provider:mpesa` (Daraja STK push); same reasoning, iOS falls back
 *    to the generic async mobile-money archetype.
 */
val iosOnlyHostedGatewayConfigs: List<HostedGatewayConfig> =
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
    )

val iosOnlyMobileMoneyConfigs: List<MobileMoneyConfig> =
    listOf(
        MobileMoneyConfig(
            gatewayId = GatewayId("mpesa"),
            displayName = "M-Pesa",
            region = "Kenya/Tanzania",
            docsPath = "docs/providers/mpesa.md",
            blurb = "Async mobile money — confirmation happens on the payer's phone, no in-app SDK/UI.",
        ),
    )
