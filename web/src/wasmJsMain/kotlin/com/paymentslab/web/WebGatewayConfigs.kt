package com.paymentslab.web

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.ReturnUrlMatchers

/**
 * The one gateway the web preview serves from the GENERIC hosted archetype that Android serves from
 * a dedicated native provider module instead — so it is absent from `:core:gateway-catalog`'s shared
 * list and would otherwise be silently dropped from the browser build.
 *
 * Everything else now comes from that shared list (see `WebKoin.initWebKoin`), which is the point:
 * this file used to hand-maintain a 13-row regional slice of a 44-row catalog. Everything stays
 * MOCK_MODE by construction — the browser build has no backend and no sandbox keys.
 *
 *  - `paystack` — Android runs `provider:paystack` (real Standard Checkout REST); that module has
 *    no wasmJs target, so the browser falls back to the generic hosted-webview archetype.
 */
val webOnlyHostedGatewayConfigs: List<HostedGatewayConfig> =
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
