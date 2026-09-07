package com.paymentslab.app

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.hostedwebview.ReturnUrlMatchers

// Paystack was the B1 vertical slice on this generic hosted config; it has since been promoted to
// its own native module (`provider:paystack`, see `PaystackGateway` + `paystackModule`) — roadmap
// item #4. Superseded here rather than kept in parallel: two `PaymentGateway`s bound to the same
// `GatewayId("paystack")` would collide in the registry.

/**
 * Shared shape for every B2 fan-out gateway shipping `MOCK_MODE` on the generic
 * `HostedWebViewAdapter`: passthrough `checkout_url`, the standard return-URL markers. Only the
 * identity/blurb differs per gateway — see each `docs/providers/<id>.md` for why it's mock, not real.
 */
private fun mockHostedGatewayConfig(
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

/**
 * B2 fan-out slice 1: Mollie. Not upgradeable to real like Paystack in this pass — Mollie requires a
 * registered business account before issuing test API keys, so there's no self-serve sandbox to
 * wire; see `docs/providers/mollie.md`. Proves the fan-out is mechanical: the generic
 * `HostedWebViewAdapter` built in B0 needed zero changes to add this gateway.
 */
val mollieHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "mollie",
        displayName = "Mollie",
        region = "EU",
        blurb =
            "Hosted checkout via Mollie's Payments API. Real integration would call " +
                "POST /v2/payments and redirect to _links.checkout — MOCK_MODE here since Mollie " +
                "needs a registered business account before issuing test keys.",
    )

/** B2 batch 2 — see docs/providers/culqi.md, ozow.md, sslcommerz.md, bkash.md for per-gateway notes. */
val culqiHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "culqi",
        displayName = "Culqi",
        region = "Peru",
        blurb =
            "Hosted checkout via Culqi's Checkout v4 (WebView + Culqi3DS). Self-serve test " +
                "keys exist (CulqiPanel) — a good future upgrade to real, not wired yet this pass.",
    )

val ozowHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "ozow",
        displayName = "Ozow",
        region = "South Africa",
        blurb =
            "Hosted instant-EFT redirect. Staging is merchant-approval-gated — no anonymous " +
                "self-serve sandbox exists for this app to wire.",
    )

val sslcommerzHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "sslcommerz",
        displayName = "SSLCommerz",
        region = "Bangladesh",
        blurb =
            "Hosted checkout. A sandbox registration flow exists, but the real session-create " +
                "contract wasn't verified against live docs this pass.",
    )

val bkashHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "bkash",
        displayName = "bKash",
        region = "Bangladesh",
        blurb =
            "Hosted/URL-based checkout. Sandbox self-serve status wasn't confirmed against " +
                "live docs this pass.",
    )

/**
 * B2 batch 3 — see `docs/providers/<id>.md` for each. All unverified-against-live-docs this pass
 * (several doc sites 404'd or didn't resolve); archetype classification is a best-effort guess
 * from general hosted-checkout conventions, not a live API check. `MOCK_MODE` only, no
 * fabricated real-API shapes.
 */
val hyperpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "hyperpay",
        displayName = "HyperPay",
        region = "MENA",
        blurb = "Hosted checkout (+ Payfort/MADA). API contract not verified against live docs this pass.",
    )

val telrHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "telr",
        displayName = "Telr",
        region = "MENA",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val myfatoorahHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "myfatoorah",
        displayName = "MyFatoorah",
        region = "Kuwait/MENA",
        blurb = "Hosted checkout (InvoiceURL-style redirect). API contract not verified this pass.",
    )

val paywayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "payway",
        displayName = "PayWay",
        region = "Cambodia",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val wipayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "wipay",
        displayName = "WiPay",
        region = "Caribbean",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val paymarkHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "paymark",
        displayName = "Paymark",
        region = "New Zealand",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val vistamoneyHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "vistamoney",
        displayName = "VistaMoney",
        region = "LATAM",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val cmiHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "cmi",
        displayName = "CMI",
        region = "Morocco",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

/** B2 batch 4 — see `docs/providers/<id>.md` for each. Same unverified-real-API caveat as batch 3. */
val myposHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "mypos",
        displayName = "myPOS",
        region = "EU",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val woyopayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "woyopay",
        displayName = "Woyo Pay",
        region = "Africa",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val amoleHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "amole",
        displayName = "Amole",
        region = "Ethiopia",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val placetopayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "placetopay",
        displayName = "PlaceToPay",
        region = "LATAM",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val paymentezHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "paymentez",
        displayName = "Paymentez",
        region = "LATAM",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val webxpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "webxpay",
        displayName = "Webxpay",
        region = "Sri Lanka",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val cardnetHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "cardnet",
        displayName = "CardNet",
        region = "Caribbean",
        blurb =
            "Needs a JS-bridge for its real integration — provider:hosted-webview only supports " +
                "return-URL interception today, so this rides the generic mock path until that's added.",
    )

val kanooHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "kanoo",
        displayName = "Kanoo",
        region = "Gulf",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val moncashHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "moncash",
        displayName = "MonCash",
        region = "Haiti",
        blurb = "Hosted checkout (+ Directpay). Minimal public docs located this pass.",
    )

/**
 * B2 batch 5 — completes the Tier-3 hosted-webview list. See `docs/providers/<id>.md` for each;
 * several (SmartPay, Thiwani, Araka, Savvy) don't even have a confirmed region — flagged honestly.
 */
val jccHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "jcc",
        displayName = "JCC",
        region = "Cyprus",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val truevoHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "truevo",
        displayName = "Truevo",
        region = "EU/Malta",
        blurb = "Hosted checkout. API contract not verified against live docs this pass.",
    )

val dotlinesHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "dotlines",
        displayName = "DotLines",
        region = "Bangladesh",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val expresspayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "expresspay",
        displayName = "ExpressPay",
        region = "Ghana",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val factranzHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "factranz",
        displayName = "FAC/PowerTranz",
        region = "Caribbean",
        blurb = "Hosted checkout (3DS-shaped flow). Minimal public docs located this pass.",
    )

val mobizpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "mobizpay",
        displayName = "Mobizpay",
        region = "Africa",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val smartpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "smartpay",
        displayName = "SmartPay",
        region = "unconfirmed",
        blurb =
            "Hosted checkout, assumed — thinnest-documented gateway in the catalog; even its " +
                "region wasn't confirmed this session.",
    )

val thiwaniHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "thiwani",
        displayName = "Thiwani",
        region = "unconfirmed",
        blurb = "Hosted checkout, assumed — region/API contract unverified this session.",
    )

val asapcardsHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "asapcards",
        displayName = "ASAP Cards",
        region = "unconfirmed",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val arakaHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "araka",
        displayName = "Araka",
        region = "unconfirmed",
        blurb = "Hosted checkout, assumed — region/API contract unverified this session.",
    )

val plugnpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "plugnpay",
        displayName = "Plug'n'Pay",
        region = "Caribbean",
        blurb = "Hosted checkout. Minimal public docs located this pass — catalog entry only.",
    )

val savvyHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "savvy",
        displayName = "Savvy",
        region = "unconfirmed",
        blurb =
            "Hosted checkout, assumed (reference branch suggests an NMI-backed white-label " +
                "integration) — unverified this session.",
    )

val acceptcardHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "acceptcard",
        displayName = "AcceptCard",
        region = "Global",
        blurb =
            "Likely Mastercard Payment Gateway Services (MPGS) Hosted Checkout — not confirmed " +
                "against live MPGS docs this session. Strong real-API upgrade candidate if confirmed.",
    )

/**
 * Tier-2 close-out — real archetype-A (native SDK) gateways demoed through the generic archetype-C
 * mock path as a simplification (real SDK wiring + a verified backend order-creation API is future
 * work; see each `docs/providers/<id>.md`, especially `phonepe.md` for verified 2026-07-03 SDK facts
 * from PhonePe's own official demo repo).
 */
val phonepeHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "phonepe",
        displayName = "PhonePe",
        region = "India",
        blurb =
            "Real SDK is phonepe.intentsdk.android.release:IntentSDK:5.1.0 (verified against " +
                "PhonePe's own demo repo) — no self-serve sandbox, demoed via the generic mock path.",
    )

val worldpayHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "worldpay",
        displayName = "Worldpay",
        region = "Global",
        blurb = "Real SDK: access-checkout-android:2.0.0. Demoed via the generic mock path.",
    )

// Paytm All-in-One was demoed on this generic hosted config; it has since been promoted to its
// own native module (`provider:paytm`, see `PaytmGateway` + `paytmModule`) — roadmap #9,
// completing the India quartet. Superseded here rather than kept in parallel: two `PaymentGateway`s
// bound to the same `GatewayId("paytmaio")` would collide in the registry.

val payuHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "payu",
        displayName = "PayU",
        region = "India/LATAM",
        blurb =
            "India has a real self-serve test SDK (payu-checkout-pro) — a strong future " +
                "real-API upgrade candidate. Demoed via the generic mock path this pass.",
    )

val ipay88HostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "ipay88",
        displayName = "iPay88",
        region = "Malaysia",
        blurb =
            "Real SDK is typically a local AAR via direct merchant agreement, not public Maven. " +
                "Demoed via the generic mock path.",
    )

val twintHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "twint",
        displayName = "TWINT",
        region = "Switzerland",
        blurb = "No direct SDK — PSP-wrapped (Datatrans/Adyen/myPOS), API itself certification-gated.",
    )

// Peach Payments was demoed here on the generic hosted config; it has since been promoted to a
// native vault-pattern module (`provider:peach`, see `PeachGateway` + `peachModule`) — roadmap #12,
// proving the stored-instrument vault generalizes across processors. Superseded here rather than
// kept in parallel: two `PaymentGateway`s bound to `GatewayId("peach")` would collide in the registry.

val areebaHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "areeba",
        displayName = "Areeba",
        region = "Lebanon/Levant",
        blurb =
            "Levant-region acquirer, hosted payment page only — no public Android SDK found. " +
                "Demoed via the generic mock path.",
    )

val conektaHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "conekta",
        displayName = "Conekta",
        region = "Mexico",
        blurb =
            "Shipped here as the generic Tier-3 hosted-checkout config. A separate KMP-native " +
                "showcase (Conekta Elements, commonMain tokenization) is tracked independently and not " +
                "part of this entry.",
    )

/**
 * Midtrans — deliberately NOT built on `com.midtrans:uikit`: Midtrans itself announced the native
 * mobile SDK (Android UIKit + iOS MidtransKit) sunsets starting June 2026. Snap Checkout (hosted
 * WebView) is Midtrans's own recommended replacement, which happens to be exactly this app's
 * archetype-C shape — a deprecation-aware choice, not a fallback.
 */
val midtransHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "midtrans",
        displayName = "Midtrans",
        region = "Indonesia",
        blurb =
            "Deliberately NOT the native uikit SDK — Midtrans announced it sunsetting June 2026. " +
                "Snap Checkout (hosted WebView) is Midtrans's own recommended replacement.",
    )

/**
 * Tier-1: PayPal. Unlike the Tier-2 batch above, this one has a real backend adapter
 * (`PayPalAdapter`) — real Orders v2 API when `PLAB_PAYPAL_TEST_CLIENT_ID`/`_CLIENT_SECRET` are
 * configured, mock fallback otherwise. Self-serve sandbox credentials, no business approval needed.
 */
val paypalHostedGatewayConfig =
    mockHostedGatewayConfig(
        id = "paypal",
        displayName = "PayPal",
        region = "Global",
        blurb =
            "Real Orders v2 REST API when sandbox credentials are configured (self-serve, no " +
                "business approval) — falls back to the generic mock path otherwise.",
    )

/** Every Tier-3 hosted-webview config wired into the app — the size of this list feeds the
 *  gateway-count claim in README.md; see `ReadmeGatewayCountTest`. */
val allHostedGatewayConfigs =
    listOf(
        mollieHostedGatewayConfig,
        culqiHostedGatewayConfig,
        ozowHostedGatewayConfig,
        sslcommerzHostedGatewayConfig,
        bkashHostedGatewayConfig,
        hyperpayHostedGatewayConfig,
        telrHostedGatewayConfig,
        myfatoorahHostedGatewayConfig,
        paywayHostedGatewayConfig,
        wipayHostedGatewayConfig,
        paymarkHostedGatewayConfig,
        vistamoneyHostedGatewayConfig,
        cmiHostedGatewayConfig,
        myposHostedGatewayConfig,
        woyopayHostedGatewayConfig,
        amoleHostedGatewayConfig,
        placetopayHostedGatewayConfig,
        paymentezHostedGatewayConfig,
        webxpayHostedGatewayConfig,
        cardnetHostedGatewayConfig,
        kanooHostedGatewayConfig,
        moncashHostedGatewayConfig,
        jccHostedGatewayConfig,
        truevoHostedGatewayConfig,
        dotlinesHostedGatewayConfig,
        expresspayHostedGatewayConfig,
        factranzHostedGatewayConfig,
        mobizpayHostedGatewayConfig,
        smartpayHostedGatewayConfig,
        thiwaniHostedGatewayConfig,
        asapcardsHostedGatewayConfig,
        arakaHostedGatewayConfig,
        plugnpayHostedGatewayConfig,
        savvyHostedGatewayConfig,
        acceptcardHostedGatewayConfig,
        phonepeHostedGatewayConfig,
        worldpayHostedGatewayConfig,
        payuHostedGatewayConfig,
        ipay88HostedGatewayConfig,
        twintHostedGatewayConfig,
        paypalHostedGatewayConfig,
        areebaHostedGatewayConfig,
        conektaHostedGatewayConfig,
        midtransHostedGatewayConfig,
    )
