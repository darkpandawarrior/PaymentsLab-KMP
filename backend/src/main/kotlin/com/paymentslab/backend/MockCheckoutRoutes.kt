package com.paymentslab.backend

import com.paymentslab.core.protocol.PaymentStatusDto
import io.ktor.http.ContentType
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one generic mock path every archetype-C (hosted-webview) and archetype-D (mobile-money)
 * gateway rides — a `MOCK_MODE` gateway needs no real sandbox to run its full lifecycle end-to-end.
 * Real adapters activate automatically once a batch wires live credentials (see `core:config`); these
 * routes stay available regardless, so a WebView can always fall back to them.
 *
 * - `GET /mock/checkout/{provider}` — a Pay/Fail landing page. `provider:hosted-webview`'s
 *   `HostedGatewayConfig.buildCheckoutUrl` points here; its `matchReturn` recognizes the
 *   `/mock/return/success` / `/mock/return/failure` redirect this page links to.
 * - `POST /mock/momo/{provider}` — schedules a delayed status flip (async mobile-money has no
 *   synchronous result at all; the client polls `GET /payments/{id}` until this fires).
 * - `POST /mock/cash/{orderId}/settle` — the cash gateway's reconciliation action: a merchant marks
 *   an order paid the moment cash is physically received. Unlike the momo flip, there is no delay/
 *   background coroutine — a human decides exactly when this fires, so it resolves synchronously.
 * - `POST /mock/xendit/{orderId}/settle` / `POST /mock/mpesa/{orderId}/settle` — the mock webhook
 *   each of those two gateways calls from `pay()` (see `provider:xendit`/`provider:mpesa`). Same
 *   idempotent-via-`applyWebhook` shape as the cash settle route, just triggered by the gateway
 *   itself instead of a human, and resolved synchronously (no artificial delay needed — the
 *   client-side gateway already fires this from a best-effort background POST).
 */
fun Route.mockCheckoutRoutes(actor: PaymentActor) {
    get("/mock/checkout/{provider}") {
        val provider = call.parameters["provider"].orBadRequest("missing_provider", "provider path param required")
        val orderId =
            call.request.queryParameters["orderId"].orBadRequest("missing_order_id", "orderId query param required")
        call.respondText(mockCheckoutHtml(provider, orderId), ContentType.Text.Html)
    }

    get("/mock/return/success") {
        val paymentId = call.request.queryParameters["payment_id"].orEmpty()
        call.respondText(mockReturnHtml("Payment succeeded", paymentId), ContentType.Text.Html)
    }

    get("/mock/return/failure") {
        val reason = call.request.queryParameters["reason"].orEmpty()
        call.respondText(mockReturnHtml("Payment failed", reason), ContentType.Text.Html)
    }

    post("/mock/momo/{provider}") {
        val provider = call.parameters["provider"].orBadRequest("missing_provider", "provider path param required")
        val orderId =
            call.request.queryParameters["orderId"].orBadRequest("missing_order_id", "orderId query param required")
        val outcome = call.request.queryParameters["outcome"] ?: "success"
        val delayMs = call.request.queryParameters["delayMs"]?.toLongOrNull() ?: DefaultMomoDelayMs

        call.application.launch {
            delay(delayMs)
            val status = if (outcome == "failure") PaymentStatusDto.FAILED else PaymentStatusDto.SUCCESS
            actor.applyWebhook(
                WebhookEvent(
                    eventId = "momo_$orderId",
                    orderId = orderId,
                    status = status,
                    paymentId = "momo_pay_$orderId",
                ),
            )
            call.application.log.info(
                "[mock-momo] $provider order=$orderId flipped to $status after ${delayMs}ms",
            )
        }
        call.respond(
            mapOf(
                "scheduled" to "true",
                "provider" to provider,
                "orderId" to orderId,
                "delayMs" to delayMs.toString(),
            ),
        )
    }

    post("/mock/cash/{orderId}/settle") {
        val orderId = call.parameters["orderId"].orBadRequest("missing_order_id", "orderId path param required")

        // Same eventId every time this order is settled → applyWebhook's dedup makes a repeat
        // settle call (double-click, retried request) a no-op rather than a second state transition.
        val result =
            actor.applyWebhook(
                WebhookEvent(
                    eventId = "cash_settle_$orderId",
                    orderId = orderId,
                    status = PaymentStatusDto.SUCCESS,
                    paymentId = "cash_pay_$orderId",
                ),
            )
        call.application.log.info("[mock-cash] order=$orderId settled duplicate=${result.duplicate}")

        call.respond(
            mapOf(
                "orderId" to orderId,
                "status" to PaymentStatusDto.SUCCESS.name,
                "duplicate" to result.duplicate.toString(),
            ),
        )
    }

    post("/mock/xendit/{orderId}/settle") {
        mockWebhookSettle(actor, provider = "xendit", eventPrefix = "xendit_settle")
    }

    post("/mock/mpesa/{orderId}/settle") {
        mockWebhookSettle(actor, provider = "mpesa", eventPrefix = "mpesa_settle")
    }
}

/**
 * Shared body for the Xendit/M-Pesa mock webhook settle routes — same idempotent-flip-to-SUCCESS
 * shape as `/mock/cash/{orderId}/settle`, factored out since both providers are near-copies.
 */
private suspend fun RoutingContext.mockWebhookSettle(
    actor: PaymentActor,
    provider: String,
    eventPrefix: String,
) {
    val orderId = call.parameters["orderId"].orBadRequest("missing_order_id", "orderId path param required")

    val result =
        actor.applyWebhook(
            WebhookEvent(
                eventId = "${eventPrefix}_$orderId",
                orderId = orderId,
                status = PaymentStatusDto.SUCCESS,
                paymentId = "${provider}_pay_$orderId",
            ),
        )
    call.application.log.info("[mock-$provider] order=$orderId settled duplicate=${result.duplicate}")

    call.respond(
        mapOf(
            "orderId" to orderId,
            "status" to PaymentStatusDto.SUCCESS.name,
            "duplicate" to result.duplicate.toString(),
        ),
    )
}

/**
 * The one place these routes reject a request. Five call sites repeated the same
 * `?: throw BadRequestException(...)` pair verbatim; folding them here means a change to how a
 * missing parameter is reported happens once, not five times.
 */
private fun String?.orBadRequest(
    code: String,
    message: String,
): String = this ?: throw BadRequestException(code, message)

private const val DefaultMomoDelayMs = 3_000L

private fun mockCheckoutHtml(
    provider: String,
    orderId: String,
) = """
    <html><body style="font-family: sans-serif; padding: 2rem;">
      <h2>Mock checkout &mdash; $provider</h2>
      <p>Order: $orderId</p>
      <p><a href="/mock/return/success?payment_id=mock_pay_$orderId">Pay successfully</a></p>
      <p><a href="/mock/return/failure?reason=card_declined">Simulate a decline</a></p>
    </body></html>
    """.trimIndent()

private fun mockReturnHtml(
    title: String,
    detail: String,
) = """
    <html><body style="font-family: sans-serif; padding: 2rem;">
      <h2>$title</h2>
      <p>$detail</p>
      <p>You may return to the app.</p>
    </body></html>
    """.trimIndent()
