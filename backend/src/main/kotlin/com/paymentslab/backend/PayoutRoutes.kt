package com.paymentslab.backend

import com.paymentslab.core.protocol.InitiatePayoutRequest
import com.paymentslab.core.protocol.PayoutResponse
import com.paymentslab.core.protocol.PayoutStatusDto
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The Transfers/payout rail (roadmap #4) — the first real payout rail in this app, mirroring the
 * order flow's idempotency + status-polling shape:
 *
 * - `POST /payouts` — initiate, idempotent on [InitiatePayoutRequest.idempotencyKey] (same
 *   putIfAbsent-race-safe dedup [PaymentStore.createOrder] uses). Always resolves to `PENDING` —
 *   there is no live provider call, because a real payout rail is KYC-gated (see `GatewayStatus`'s
 *   own doc comment); this app can't prove sandbox-readiness for moving money out.
 * - `POST /mock/payouts/{payoutId}/settle` — the mock settlement webhook. Flips `PENDING` → the
 *   given outcome after a delay, same shape as [mockCheckoutRoutes]'s `/mock/momo/{provider}` flip
 *   for async mobile-money — a payout settling is exactly that kind of async, no-synchronous-result
 *   flow, so it rides the same pattern rather than a fake instant success.
 * - `GET /payouts/{payoutId}` — the polling target.
 */
@Suppress("ThrowsCount") // legitimate per-route request/lookup guards, mirrors Application.module's routing
fun Route.payoutRoutes(store: PayoutStore) {
    post("/payouts") {
        val req = call.receive<InitiatePayoutRequest>()
        val payoutId = "payout_${UUID.randomUUID()}"

        val creation =
            store.initiate(
                payoutId = payoutId,
                gatewayId = req.gatewayId,
                recipientRef = req.recipientRef,
                amountMinor = req.amountMinor,
                currency = req.currency,
                idempotencyKey = req.idempotencyKey,
            )
        val record = creation.record

        call.application.log.info(
            "[payouts] initiated ${record.payoutId} gw=${record.gatewayId} " +
                "amount=${record.amountMinor}${record.currency}" +
                if (!creation.isNew) " (idempotent replay)" else "",
        )

        call.respond(record.toResponse())
    }

    get("/payouts/{payoutId}") {
        val payoutId =
            call.parameters["payoutId"]
                ?: throw BadRequestException("missing_payout_id", "payoutId path param required")
        val record =
            store.get(payoutId)
                ?: throw NotFoundException("unknown_payout", "No payout: $payoutId")
        call.respond(record.toResponse())
    }

    // Mock settlement webhook — schedules a delayed status flip, exactly like /mock/momo/{provider}.
    post("/mock/payouts/{payoutId}/settle") {
        val payoutId =
            call.parameters["payoutId"]
                ?: throw BadRequestException("missing_payout_id", "payoutId path param required")
        store.get(payoutId)
            ?: throw NotFoundException("unknown_payout", "No payout: $payoutId")
        val outcome = call.request.queryParameters["outcome"] ?: "settled"
        val delayMs = call.request.queryParameters["delayMs"]?.toLongOrNull() ?: DefaultSettleDelayMs

        call.application.launch {
            delay(delayMs)
            val status = if (outcome == "failed") PayoutStatusDto.FAILED else PayoutStatusDto.SETTLED
            store.markSettled(payoutId, status)
            call.application.log.info("[mock-payout-settle] $payoutId flipped to $status after ${delayMs}ms")
        }
        call.respond(mapOf("scheduled" to "true", "payoutId" to payoutId, "delayMs" to delayMs.toString()))
    }
}

private const val DefaultSettleDelayMs = 3_000L

private fun PayoutStore.PayoutRecord.toResponse() =
    PayoutResponse(
        payoutId = payoutId,
        gatewayId = gatewayId,
        recipientRef = recipientRef,
        amountMinor = amountMinor,
        currency = currency,
        status = status,
        updatedAtEpochMs = updatedAtEpochMs,
    )
