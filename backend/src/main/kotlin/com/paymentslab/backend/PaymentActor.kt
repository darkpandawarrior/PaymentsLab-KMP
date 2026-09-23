package com.paymentslab.backend

import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.VerifyRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Linearizes every mutation of a [PaymentStore] record through one consumer coroutine, so concurrent
 * requests for the same order (a client's `/verify` racing a provider webhook, or two redelivered
 * webhooks) never interleave a check-then-act sequence — idempotency without DB row locks. Mirrors
 * Gaddi's `MatchActor` channel-actor pattern (single [Channel] + sealed `Command` + one consumer
 * loop), generalized from match-state to payment-state.
 */
class PaymentActor(
    private val store: PaymentStore,
    scope: CoroutineScope,
) {
    private sealed interface Command {
        data class Verify(
            val req: VerifyRequest,
            val adapter: GatewayAdapter,
            val reply: CompletableDeferred<PaymentStatusDto>,
        ) : Command

        data class ApplyWebhook(
            val event: WebhookEvent,
            val reply: CompletableDeferred<PaymentStore.WebhookResult>,
        ) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Verify -> {
                        val status = command.adapter.verify(command.req)
                        store.recordVerification(
                            orderId = command.req.orderId,
                            status = status,
                            paymentId = command.req.paymentId,
                            providerRef = command.req.paymentId,
                        )
                        command.reply.complete(status)
                    }
                    is Command.ApplyWebhook -> {
                        val result =
                            store.applyWebhook(
                                eventId = command.event.eventId,
                                orderId = command.event.orderId,
                                status = command.event.status,
                                paymentId = command.event.paymentId,
                                providerRef = command.event.paymentId,
                            )
                        command.reply.complete(result)
                    }
                }
            }
        }
    }

    /** Resolve a client-side verify claim against [adapter], then record it — linearized per order. */
    suspend fun verify(
        req: VerifyRequest,
        adapter: GatewayAdapter,
    ): PaymentStatusDto {
        val reply = CompletableDeferred<PaymentStatusDto>()
        commands.send(Command.Verify(req, adapter, reply))
        return reply.await()
    }

    /** Apply an inbound (already signature-verified) webhook event idempotently. */
    suspend fun applyWebhook(event: WebhookEvent): PaymentStore.WebhookResult {
        val reply = CompletableDeferred<PaymentStore.WebhookResult>()
        commands.send(Command.ApplyWebhook(event, reply))
        return reply.await()
    }
}
