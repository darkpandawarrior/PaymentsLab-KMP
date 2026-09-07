package com.paymentslab.app

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.provider.mobilemoney.MobileMoneyConfig

/**
 * Archetype-D fan-out: `pay()` never launches anything (confirmation happens on the payer's phone
 * outside the app), so every one of these is a one-liner — the same mechanical leverage the
 * archetype-C `HostedGatewayConfig` fan-out proved, just for the async/poll shape instead of the
 * hosted-checkout shape. See each `docs/providers/<id>.md`.
 */
private fun momoConfig(
    id: String,
    displayName: String,
    region: String,
) = MobileMoneyConfig(
    gatewayId = GatewayId(id),
    displayName = displayName,
    region = region,
    docsPath = "docs/providers/$id.md",
    blurb = "Async mobile money — confirmation happens on the payer's phone, no in-app SDK/UI.",
)

val mtnMomoConfig = momoConfig("mtnmomo", "MTN MoMo", "Africa")
val beyonicConfig = momoConfig("beyonic", "Beyonic", "Africa")
val orangeMoneyConfig = momoConfig("orangemoney", "Orange Money", "Africa")
val waveConfig = momoConfig("wave", "Wave", "Senegal")
val ecocashConfig = momoConfig("ecocash", "EcoCash", "Zimbabwe")
val easypaisaConfig = momoConfig("easypaisa", "Easypaisa", "Pakistan")
val vukapayConfig = momoConfig("vukapay", "VukaPay", "Africa")

/** Every mobile-money config wired into the app — see `ReadmeGatewayCountTest`. */
val allMobileMoneyConfigs =
    listOf(
        mtnMomoConfig,
        beyonicConfig,
        orangeMoneyConfig,
        waveConfig,
        ecocashConfig,
        easypaisaConfig,
        vukapayConfig,
    )
