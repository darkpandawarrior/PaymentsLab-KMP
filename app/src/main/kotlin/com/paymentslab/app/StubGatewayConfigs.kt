package com.paymentslab.app

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.StubGateway
import com.siddharth.kmp.paymentsapi.StubGatewayConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Tier-4 "stub/docs-only" fan-out (B5): catalog visibility + a web-researched doc, no working
 * integration. See each `docs/providers/<id>.md`.
 */
private fun stubConfig(
    id: String,
    displayName: String,
    region: String,
    blurb: String,
) = StubGatewayConfig(
    id = GatewayId(id),
    displayName = displayName,
    region = region,
    docsPath = "docs/providers/$id.md",
    blurb = blurb,
)

val cybersourceStubConfig =
    stubConfig(
        id = "cybersource",
        displayName = "Cybersource",
        region = "Global",
        blurb = "Visa-owned enterprise payment platform — REST/SOAP APIs, no consumer-facing Android SDK.",
    )

// NMI was catalog-only here (docs stub); it has since been promoted to a native vault-pattern
// module (`provider:nmi`, see `NmiGateway` + `nmiModule`) — roadmap #12, proving the stored-instrument
// vault generalizes across processors. Superseded here rather than kept in parallel: two
// `PaymentGateway`s bound to `GatewayId("nmi")` would collide in the registry.

val selcomStubConfig =
    stubConfig(
        id = "selcom",
        displayName = "Selcom",
        region = "Tanzania",
        blurb = "Tanzanian mobile money + card PSP — hosted checkout/USSD only, no public Android SDK found.",
    )

val supaGhanaPayStubConfig =
    stubConfig(
        id = "supaghanapay",
        displayName = "Supa Ghana Pay",
        region = "Ghana",
        blurb = "Ghanaian mobile money aggregator — no public documentation or Android SDK found this session.",
    )

/** Every stub/docs-only config wired into the app — see `ReadmeGatewayCountTest`. */
val allStubGatewayConfigs = listOf(cybersourceStubConfig, selcomStubConfig, supaGhanaPayStubConfig)

/** One [PaymentGateway] per [StubGatewayConfig] — mirrors `mobileMoneyModule`'s list-driven shape. */
fun stubGatewayModule(configs: List<StubGatewayConfig>) =
    module {
        configs.forEach { config ->
            single<PaymentGateway>(qualifier = named(config.id.value)) { StubGateway(config) }
        }
    }
