package com.paymentslab.backend

import com.paymentslab.core.protocol.ApiError
import com.paymentslab.core.protocol.CreateOrderRequest
import com.paymentslab.core.protocol.OrderResponse
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.PaymentStatusResponse
import com.paymentslab.core.protocol.VerifyRequest
import com.paymentslab.core.protocol.VerifyResponse
import com.paymentslab.core.protocol.WebhookAck
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

/** JSON config shared by ContentNegotiation and raw webhook-body decoding. */
val BackendJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** One-liner for a B2 fan-out gateway riding the generic archetype-C adapter, MOCK_MODE only. */
private fun mockHostedAdapter(
    gatewayId: String,
    displayName: String,
    publicBaseUrl: String,
) = HostedWebViewAdapter(HostedGatewayServerConfig(gatewayId, displayName), publicBaseUrl)

/**
 * Ktor application module. Wires plugins + routes against freshly-built services so a `testApplication`
 * gets an isolated in-memory store per run.
 *
 * The entrypoint [main] binds Netty; tests call `application { module() }` with no bound port.
 *
 * LongMethod/ThrowsCount are suppressed by design: this is a framework wiring function (plugin
 * installation + route table), and the throw-count is from per-route request validation nested in
 * routing lambdas, which is idiomatic Ktor rather than a complexity smell.
 */
@Suppress("LongMethod", "ThrowsCount")
fun Application.module(config: ServerConfig = ServerConfig.fromEnv()) {
    val store = PaymentStore()
    val actor = PaymentActor(store, this)
    val catalog = CatalogService()
    val ledger = LedgerStore()
    val payoutStore = PayoutStore()
    val mandateStore = MandateStore()
    val vaultStore = VaultStore()
    val connectStore = ConnectStore()
    val outboundHttpClient = HttpClient(OkHttp) { install(ClientContentNegotiation) { json(BackendJson) } }
    val gateways =
        GatewayRegistry(
            listOf(
                RazorpayAdapter(
                    keyId = config.razorpayKeyId,
                    secret = config.razorpaySecret,
                    webhookSecret = config.razorpayWebhookSecret,
                ),
                PaystackAdapter(
                    credentials = config.paystackCredentials,
                    publicBaseUrl = config.publicBaseUrl,
                    httpClient = outboundHttpClient,
                ),
                PayPalAdapter(
                    credentials = config.paypalCredentials,
                    publicBaseUrl = config.publicBaseUrl,
                    httpClient = outboundHttpClient,
                ),
                SquareAdapter(
                    credentials = config.squareCredentials,
                    httpClient = outboundHttpClient,
                ),
                OmiseAdapter(
                    credentials = config.omiseCredentials,
                    httpClient = outboundHttpClient,
                ),
                UpiIntentAdapter(
                    payeeVpa = "paymentslab@upi",
                    payeeName = "PaymentsLab-KMP",
                    merchantCategoryCode = "5411",
                ),
                StripeAdapter(publishableKey = config.stripePublishableKey, secret = config.stripeSecret),
                CashfreeAdapter(appId = config.cashfreeAppId, secret = config.cashfreeSecret),
                GooglePayAdapter(),
                // Flutterwave's client module (`provider:flutterwave`) rides the same
                // HostedCheckoutRelay/checkout_url mechanics as every generic archetype-C gateway below
                // (see docs/providers/flutterwave.md) — no dedicated adapter needed, same as paytm.
                mockHostedAdapter("flutterwave", "Flutterwave", config.publicBaseUrl),
                // B2 fan-out: MOCK_MODE only — see each docs/providers/<id>.md for why. All ride the
                // generic archetype-C adapter untouched since B0; proves the fan-out is mechanical.
                mockHostedAdapter("mollie", "Mollie", config.publicBaseUrl),
                mockHostedAdapter("culqi", "Culqi", config.publicBaseUrl),
                mockHostedAdapter("ozow", "Ozow", config.publicBaseUrl),
                mockHostedAdapter("sslcommerz", "SSLCommerz", config.publicBaseUrl),
                mockHostedAdapter("bkash", "bKash", config.publicBaseUrl),
                mockHostedAdapter("hyperpay", "HyperPay", config.publicBaseUrl),
                mockHostedAdapter("telr", "Telr", config.publicBaseUrl),
                mockHostedAdapter("myfatoorah", "MyFatoorah", config.publicBaseUrl),
                mockHostedAdapter("payway", "PayWay", config.publicBaseUrl),
                mockHostedAdapter("wipay", "WiPay", config.publicBaseUrl),
                mockHostedAdapter("paymark", "Paymark", config.publicBaseUrl),
                mockHostedAdapter("vistamoney", "VistaMoney", config.publicBaseUrl),
                mockHostedAdapter("cmi", "CMI", config.publicBaseUrl),
                mockHostedAdapter("mypos", "myPOS", config.publicBaseUrl),
                mockHostedAdapter("woyopay", "Woyo Pay", config.publicBaseUrl),
                mockHostedAdapter("amole", "Amole", config.publicBaseUrl),
                mockHostedAdapter("placetopay", "PlaceToPay", config.publicBaseUrl),
                mockHostedAdapter("paymentez", "Paymentez", config.publicBaseUrl),
                mockHostedAdapter("webxpay", "Webxpay", config.publicBaseUrl),
                mockHostedAdapter("cardnet", "CardNet", config.publicBaseUrl),
                mockHostedAdapter("kanoo", "Kanoo", config.publicBaseUrl),
                mockHostedAdapter("moncash", "MonCash", config.publicBaseUrl),
                mockHostedAdapter("jcc", "JCC", config.publicBaseUrl),
                mockHostedAdapter("truevo", "Truevo", config.publicBaseUrl),
                mockHostedAdapter("dotlines", "DotLines", config.publicBaseUrl),
                mockHostedAdapter("expresspay", "ExpressPay", config.publicBaseUrl),
                mockHostedAdapter("factranz", "FAC/PowerTranz", config.publicBaseUrl),
                mockHostedAdapter("mobizpay", "Mobizpay", config.publicBaseUrl),
                mockHostedAdapter("smartpay", "SmartPay", config.publicBaseUrl),
                mockHostedAdapter("thiwani", "Thiwani", config.publicBaseUrl),
                mockHostedAdapter("asapcards", "ASAP Cards", config.publicBaseUrl),
                mockHostedAdapter("araka", "Araka", config.publicBaseUrl),
                mockHostedAdapter("plugnpay", "Plug'n'Pay", config.publicBaseUrl),
                mockHostedAdapter("savvy", "Savvy", config.publicBaseUrl),
                mockHostedAdapter("acceptcard", "AcceptCard", config.publicBaseUrl),
                mockHostedAdapter("phonepe", "PhonePe", config.publicBaseUrl),
                mockHostedAdapter("worldpay", "Worldpay", config.publicBaseUrl),
                mockHostedAdapter("paytmaio", "Paytm All-in-One", config.publicBaseUrl),
                mockHostedAdapter("payu", "PayU", config.publicBaseUrl),
                mockHostedAdapter("ipay88", "iPay88", config.publicBaseUrl),
                mockHostedAdapter("twint", "TWINT", config.publicBaseUrl),
                mockHostedAdapter("peach", "Peach Payments", config.publicBaseUrl),
                mockHostedAdapter("areeba", "Areeba", config.publicBaseUrl),
                mockHostedAdapter("conekta", "Conekta", config.publicBaseUrl),
                mockHostedAdapter("midtrans", "Midtrans", config.publicBaseUrl),
                // Archetype D (async mobile-money) — same MOCK_MODE adapter shape as B0 built it,
                // registered for the first time now that provider:mobile-money exists client-side.
                // Xendit + M-Pesa (roadmap #10) moved off the shared momo-flip/hosted-webview paths
                // onto their own dedicated mock settle routes (`/mock/xendit`, `/mock/mpesa`) — see
                // `provider:xendit`/`provider:mpesa` and `MockCheckoutRoutes.kt` — but server-side
                // order creation still rides this same generic archetype-D adapter shape unchanged.
                MobileMoneyAdapter(HostedGatewayServerConfig("xendit", "Xendit")),
                MobileMoneyAdapter(HostedGatewayServerConfig("mpesa", "M-Pesa")),
                MobileMoneyAdapter(HostedGatewayServerConfig("mtnmomo", "MTN MoMo")),
                MobileMoneyAdapter(HostedGatewayServerConfig("beyonic", "Beyonic")),
                MobileMoneyAdapter(HostedGatewayServerConfig("orangemoney", "Orange Money")),
                MobileMoneyAdapter(HostedGatewayServerConfig("wave", "Wave")),
                MobileMoneyAdapter(HostedGatewayServerConfig("ecocash", "EcoCash")),
                MobileMoneyAdapter(HostedGatewayServerConfig("easypaisa", "Easypaisa")),
                MobileMoneyAdapter(HostedGatewayServerConfig("vukapay", "VukaPay")),
                // Archetype D-simplest (record-only cash) — resolved only via a merchant hitting the
                // mock settle route, no webhook/SDK involved at all.
                CashAdapter(),
            ),
        )

    install(ContentNegotiation) { json(BackendJson) }

    // TAG-style request logging via slf4j (Ktor CallLogging → logback console appender).
    install(CallLogging) { level = Level.INFO }

    // Permissive CORS for local dev only — a real deployment locks this to known origins.
    install(CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.application.log.warn("[ApiException] ${cause.code}: ${cause.message}")
            call.respond(cause.status, ApiError(cause.code, cause.message))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("[Unhandled] ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("internal_error", cause.message ?: "Unexpected server error"),
            )
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        // ── Catalog ─────────────────────────────────────────────────────────
        get("/catalog") {
            call.respond(catalog.all())
        }

        // ── Create order ────────────────────────────────────────────────────
        // Price comes from CatalogService (server-side); any client-sent amount is irrelevant —
        // CreateOrderRequest carries no amount by design. This is the trust boundary.
        post("/orders") {
            val req = call.receive<CreateOrderRequest>()
            val item =
                catalog.find(req.catalogItemId)
                    ?: throw BadRequestException("unknown_catalog_item", "No catalog item: ${req.catalogItemId}")
            val adapter =
                gateways.find(req.gatewayId)
                    ?: throw BadRequestException("unknown_gateway", "No gateway: ${req.gatewayId}")

            val candidateOrderId = "order_${UUID.randomUUID()}"
            val creation =
                store.createOrder(
                    orderId = candidateOrderId,
                    catalogItemId = item.id,
                    gatewayId = adapter.gatewayId,
                    amountMinor = item.amountMinor,
                    currency = item.currency,
                    idempotencyKey = req.idempotencyKey,
                )
            val record = creation.record

            // Only the FIRST request for this idempotencyKey talks to the provider — a retry replay
            // must not mint a second live provider-side order (that's the double-charge this closes).
            val providerParams =
                if (creation.isNew) {
                    adapter.createProviderOrder(record.orderId, item).also {
                        store.recordProviderParams(record.orderId, it)
                    }
                } else {
                    record.providerParams
                }
            call.application.log.info(
                "[orders] created ${record.orderId} item=${item.id} " +
                    "amount=${item.amountMinor}${item.currency} gw=${adapter.gatewayId}" +
                    if (!creation.isNew) " (idempotent replay)" else "",
            )

            call.respond(
                OrderResponse(
                    orderId = record.orderId,
                    catalogItemId = item.id,
                    amountMinor = item.amountMinor,
                    currency = item.currency,
                    gatewayId = adapter.gatewayId,
                    providerParams = providerParams,
                ),
            )
        }

        // ── Verify a client-side payment result ─────────────────────────────
        post("/payments/{orderId}/verify") {
            val orderId =
                call.parameters["orderId"]
                    ?: throw BadRequestException("missing_order_id", "orderId path param required")
            val req = call.receive<VerifyRequest>()
            store.get(orderId)
                ?: throw NotFoundException("unknown_order", "No order: $orderId")
            val adapter =
                gateways.find(req.gatewayId)
                    ?: throw BadRequestException("unknown_gateway", "No gateway: ${req.gatewayId}")

            val status = actor.verify(req.copy(orderId = orderId), adapter)
            call.application.log.info("[verify] order=$orderId gw=${req.gatewayId} status=$status")

            val message =
                when (status) {
                    PaymentStatusDto.SUCCESS -> "Payment verified."
                    PaymentStatusDto.PENDING -> "Awaiting provider confirmation (webhook)."
                    PaymentStatusDto.FAILED -> "Signature verification failed."
                    else -> null
                }
            call.respond(VerifyResponse(status = status, paymentId = req.paymentId, message = message))
        }

        // ── Provider webhook (idempotent) ───────────────────────────────────
        post("/webhooks/{provider}") {
            val provider =
                call.parameters["provider"]
                    ?: throw BadRequestException("missing_provider", "provider path param required")
            val adapter =
                gateways.find(provider)
                    ?: throw BadRequestException("unknown_gateway", "No gateway: $provider")
            val rawBody = call.receiveText()
            val headers =
                call.request.headers
                    .names()
                    .associateWith { call.request.headers[it].orEmpty() }

            // Dispatched through the adapter — no more razorpay-only special case. Every provider
            // decides its own webhook authenticity rule (default: accept, see GatewayAdapter).
            when (val verification = adapter.verifyWebhook(rawBody, headers)) {
                is WebhookVerification.Rejected ->
                    throw UnauthorizedException("bad_signature", verification.reason)
                WebhookVerification.Accepted -> Unit
            }

            val event = BackendJson.decodeFromString(WebhookEvent.serializer(), rawBody)
            val result = actor.applyWebhook(event)
            call.application.log.info(
                "[webhook] provider=$provider event=${event.eventId} " +
                    "duplicate=${result.duplicate} status=${event.status}",
            )

            call.respond(WebhookAck(received = true, eventId = event.eventId, duplicate = result.duplicate))
        }

        mockCheckoutRoutes(actor)

        walletLedgerRoutes(ledger)

        payoutRoutes(payoutStore)

        mandateRoutes(mandateStore, catalog, gateways)

        vaultRoutes(vaultStore, catalog)

        connectRoutes(connectStore, payoutStore)

        // ── Poll payment status ─────────────────────────────────────────────
        get("/payments/{orderId}") {
            val orderId =
                call.parameters["orderId"]
                    ?: throw BadRequestException("missing_order_id", "orderId path param required")
            val record =
                store.get(orderId)
                    ?: throw NotFoundException("unknown_order", "No order: $orderId")

            call.respond(
                PaymentStatusResponse(
                    orderId = record.orderId,
                    paymentId = record.paymentId,
                    status = record.status,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    providerRef = record.providerRef,
                ),
            )
        }
    }
}
