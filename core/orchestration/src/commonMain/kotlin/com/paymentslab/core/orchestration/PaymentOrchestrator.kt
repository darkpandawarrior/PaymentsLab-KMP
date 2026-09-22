package com.paymentslab.core.orchestration

import com.paymentslab.core.orchestration.fsm.FsmPollConfig
import com.paymentslab.core.orchestration.fsm.PaymentEffect
import com.paymentslab.core.orchestration.fsm.PaymentEvent
import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.paymentslab.core.orchestration.fsm.PaymentReducer
import com.paymentslab.core.orchestration.fsm.PaymentState
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentBackend
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentSnapshot
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import com.siddharth.kmp.paymentsapi.PendingPayment
import com.siddharth.kmp.paymentsapi.PendingPaymentJournal
import com.siddharth.kmp.paymentsapi.Redactor
import com.siddharth.kmp.paymentsapi.SplitLeg
import com.siddharth.kmp.paymentsapi.VerificationRequest
import com.siddharth.kmp.paymentsapi.WalletLedgerPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The effectful shell around the pure [PaymentReducer]. The reducer decides *what* to do next; this
 * class *does* it — creating orders, journaling, launching the gateway, verifying and polling — feeds
 * each result back into the reducer as an event, and emits a [PaymentStep] the Lab renders as a
 * live timeline. It holds no branching decisions of its own; that logic is the (fully unit-tested)
 * reducer's job. See `core/orchestration/.../fsm/PaymentFsm.kt`.
 *
 * Invariants (enforced by the reducer + this interpreter):
 *  1. **Journal before launch** — the pending row is written before the SDK opens (process-death insurance).
 *  2. **Server is truth** — a client `Success` is never terminal on its own; it is always confirmed
 *     via [PaymentBackend.verify] (and polled if still `PENDING`) before the payment settles.
 *
 * Every collaborator is an interface, so the whole flow is exercised in `commonTest` with fakes.
 */
class PaymentOrchestrator(
    private val registry: PaymentGatewayRegistry,
    private val backend: PaymentBackend,
    private val journal: PendingPaymentJournal,
    private val pollConfig: PollConfig = PollConfig(),
    private val now: () -> Long = ::systemNowMs,
) {
    /**
     * @param idempotencyKey caller-owned, stable across retries of the SAME logical order attempt
     *   (e.g. the user re-pressing Pay after an ambiguous result) so the server dedups instead of
     *   minting a second live order. The caller (ViewModel) mints a fresh key only for a genuinely
     *   new attempt — after success or a changed selection.
     */
    fun pay(
        host: PaymentHost,
        gatewayId: GatewayId,
        catalogItemId: String,
        idempotencyKey: String,
    ): Flow<PaymentStep> =
        flow {
            val gateway = registry.byId(gatewayId)
            if (gateway == null) {
                emit(PaymentStep.Errored(UiText.Dynamic("No gateway registered for '${gatewayId.value}'")))
                return@flow
            }
            driveFsm(host, gateway, gatewayId, catalogItemId, idempotencyKey) { emit(it) }
        }

    /**
     * Split payment: one logical purchase paid as two legs — [walletAmount] debited from the wallet
     * ([walletGatewayId], [walletLedgerPort]) first, the remainder charged via [gatewayId] second.
     * Modeled as two independent FSM runs (reusing [driveFsm], the same machine [pay] drives) against
     * two orders priced by the SAME [catalogItemId] — the wallet order capped to [walletAmount], the
     * gateway order for the rest. Per-leg idempotency keys (`"$idempotencyKey:wallet"` /
     * `"$idempotencyKey:gateway"`) make replaying the whole split safe: each leg's `createOrder` and
     * each gateway's own internal idempotency (keyed off its stable per-leg orderId) dedups exactly
     * as a single [pay] call would.
     *
     * Compensation: if the gateway leg fails to reach [PaymentStatus.SUCCESS], the wallet leg's debit
     * is reversed with a compensating credit ([WalletLedgerPort.refund]) so the user is never left
     * partially charged — net wallet movement zero. Guard: if the wallet leg itself can't complete
     * (e.g. insufficient balance), the gateway is never touched.
     */
    fun paySplit(
        host: PaymentHost,
        walletGatewayId: GatewayId,
        walletAccountId: String,
        walletAmount: Money,
        walletLedgerPort: WalletLedgerPort,
        gatewayId: GatewayId,
        catalogItemId: String,
        idempotencyKey: String,
    ): Flow<PaymentStep> =
        flow {
            val walletGateway = registry.byId(walletGatewayId)
            val gateway = registry.byId(gatewayId)
            if (walletGateway == null || gateway == null) {
                val missing = if (walletGateway == null) walletGatewayId else gatewayId
                emit(PaymentStep.Errored(UiText.Dynamic("No gateway registered for '${missing.value}'")))
                return@flow
            }

            val walletKey = "$idempotencyKey:wallet"
            val gatewayKey = "$idempotencyKey:gateway"

            val walletSettled =
                driveFsm(host, walletGateway, walletGatewayId, catalogItemId, walletKey, capAmount = walletAmount) {
                    emit(if (it is PaymentStep.Settled) PaymentStep.LegSettled(SplitLeg.WALLET, it) else it)
                }

            if (walletSettled.status != PaymentStatus.SUCCESS) {
                // Wallet leg never actually moved money (or the FSM already settled it FAILED/CANCELLED) —
                // guard: fail clean, the gateway leg is never attempted.
                return@flow
            }

            val gatewaySettled =
                driveFsm(host, gateway, gatewayId, catalogItemId, gatewayKey) {
                    emit(if (it is PaymentStep.Settled) PaymentStep.LegSettled(SplitLeg.GATEWAY, it) else it)
                }

            if (gatewaySettled.status != PaymentStatus.SUCCESS) {
                compensateWalletLeg(walletLedgerPort, walletAccountId, walletAmount, walletKey) { emit(it) }
            }
        }

    /**
     * Reverse the wallet leg's debit — the split-payment compensating credit.
     *
     * Compensation is the last line of defence against a half-charged user, so a failure here is
     * reported, never propagated: letting it escape would abort the flow with the wallet leg still
     * debited and no Errored step emitted. CancellationException is rethrown first, hence the
     * suppression rather than a narrower catch.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun compensateWalletLeg(
        walletLedgerPort: WalletLedgerPort,
        walletAccountId: String,
        walletAmount: Money,
        walletKey: String,
        emit: suspend (PaymentStep) -> Unit,
    ) {
        try {
            val refundKey = "$walletKey:compensate"
            val txnId = walletLedgerPort.refund(walletAccountId, refundKey, walletAmount.amountMinor)
            emit(
                PaymentStep.Compensated(
                    walletAmount = walletAmount,
                    refundTxnId = txnId,
                    payload = Redactor.redact("wallet_compensated", mapOf("txn_id" to txnId)),
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppLog.e("Compensating wallet refund failed for key=$walletKey", t, tag = TAG)
            emit(PaymentStep.Errored(UiText.of("Wallet compensation failed: ${t.message}")))
        }
    }

    /**
     * Drives the FSM for one leg to a terminal state, optionally capping the created order's amount
     * to [capAmount] (used by the split's wallet leg, which pays only a portion of the priced order).
     * Shared by [pay] and [paySplit] so both go through the identical, fully-tested reducer loop.
     *
     * The FSM boundary. A payment that throws must still be resolved in the journal and reported as
     * a terminal step, otherwise process-death recovery finds a pending row forever. Effects call
     * into gateway SDKs whose throw surface this module does not own, so the catch is total by
     * necessity; CancellationException is rethrown first.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun driveFsm(
        host: PaymentHost,
        gateway: PaymentGateway,
        gatewayId: GatewayId,
        catalogItemId: String,
        idempotencyKey: String,
        capAmount: Money? = null,
        emit: suspend (PaymentStep) -> Unit,
    ): PaymentSnapshot {
        val fsmPoll = FsmPollConfig(maxAttempts = pollConfig.maxAttempts)
        var transition = PaymentReducer.start(catalogItemId, gatewayId)
        var created: CreatedOrder? = null

        try {
            // Drive the machine: execute the current effect, feed its result back as an event, repeat.
            while (transition.state.phase != PaymentPhase.TERMINAL) {
                val effect = transition.effects.single()
                val event =
                    when (effect) {
                        PaymentEffect.CreateOrder -> {
                            val raw = backend.createOrder(catalogItemId, gatewayId, idempotencyKey)
                            val c = if (capAmount != null) raw.copy(order = raw.order.copy(amount = capAmount)) else raw
                            created = c
                            emit(
                                PaymentStep.OrderCreated(
                                    orderId = c.order.orderId,
                                    amount = c.order.amount,
                                    payload =
                                        Redactor.redact(
                                            "order",
                                            c.providerParams + mapOf("order_id" to c.order.orderId),
                                        ),
                                ),
                            )
                            PaymentEvent.OrderCreated(c.order.orderId)
                        }

                        PaymentEffect.RecordJournalAndLaunch -> {
                            val c = requireNotNull(created)
                            // Journal BEFORE launch — the process-death insurance.
                            journal.record(
                                PendingPayment(
                                    orderId = c.order.orderId,
                                    catalogItemId = catalogItemId,
                                    gatewayId = gatewayId,
                                    amount = c.order.amount,
                                    createdAtEpochMs = now(),
                                    status = PaymentStatus.CREATED,
                                ),
                            )
                            emit(PaymentStep.Launching(gatewayId))
                            val prepared = gateway.prepare(c)
                            val result = gateway.pay(host, prepared)
                            emit(PaymentStep.ClientResult(result, result.raw))
                            PaymentEvent.ClientReturned(result)
                        }

                        is PaymentEffect.Verify -> {
                            emit(PaymentStep.Verifying())
                            val snapshot =
                                backend.verify(
                                    verificationRequest(gatewayId, transition.state.orderId!!, effect.result),
                                )
                            PaymentEvent.ServerAnswered(snapshot)
                        }

                        PaymentEffect.CheckStatus -> {
                            val orderId = transition.state.orderId!!
                            val snapshot =
                                if (transition.state.phase == PaymentPhase.POLLING) {
                                    // Polling loop — back off, then ask the server for authoritative state.
                                    delay(backoffDelayMs(transition.state.pollAttempts))
                                    backend.status(orderId)
                                } else {
                                    // First server consult after a client-reported failure (server can disagree).
                                    emit(PaymentStep.Verifying())
                                    backend.status(orderId)
                                }
                            PaymentEvent.ServerAnswered(snapshot)
                        }

                        is PaymentEffect.Settle -> error("Settle is terminal and handled after the loop")
                    }
                transition = PaymentReducer.reduce(transition.state, event, fsmPoll)
            }

            return settle(transition.state, emit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppLog.e("Payment flow failed for order=${transition.state.orderId}", t, tag = TAG)
            transition.state.orderId?.let { journal.markResolved(it, PaymentStatus.FAILED, null) }
            emit(PaymentStep.Errored(UiText.of(t.message ?: "Payment failed")))
            return PaymentSnapshot(transition.state.orderId ?: "", transition.state.paymentId, PaymentStatus.FAILED)
        }
    }

    /** Run the terminal [PaymentEffect.Settle]: resolve the journal and emit the final step. */
    private suspend fun settle(
        state: PaymentState,
        emit: suspend (PaymentStep) -> Unit,
    ): PaymentSnapshot {
        val status = requireNotNull(state.terminalStatus)
        val orderId = state.orderId
        orderId?.let { journal.markResolved(it, status, state.paymentId) }
        val snapshot = PaymentSnapshot(orderId ?: "", state.paymentId, status)
        emit(
            PaymentStep.Settled(
                status = status,
                snapshot = snapshot,
                payload =
                    Redactor.redact(
                        "settled",
                        mapOf("order_id" to orderId, "status" to status.name, "payment_id" to state.paymentId),
                    ),
            ),
        )
        return snapshot
    }

    private fun verificationRequest(
        gatewayId: GatewayId,
        orderId: String,
        result: PaymentResult,
    ): VerificationRequest =
        when (result) {
            is PaymentResult.Success ->
                VerificationRequest(
                    gatewayId = gatewayId,
                    orderId = orderId,
                    paymentId = result.paymentId,
                    signature = result.verification["signature"],
                    extra = result.verification,
                )
            is PaymentResult.Pending ->
                VerificationRequest(gatewayId = gatewayId, orderId = orderId, extra = result.verification)
            else -> VerificationRequest(gatewayId = gatewayId, orderId = orderId)
        }

    private fun backoffDelayMs(attempt: Int): Long {
        var delayMs = pollConfig.initialDelayMs
        repeat((attempt - 1).coerceAtLeast(0)) { delayMs = (delayMs * 2).coerceAtMost(pollConfig.maxDelayMs) }
        return delayMs
    }

    /**
     * Cold-start recovery: for every payment written to the journal but never resolved (app died
     * mid-flight), ask the server what actually happened and settle the row. Called on app launch
     * and by the WorkManager reconciliation worker.
     *
     * Per-order isolation: one unreachable order must not abort the whole recovery sweep, so each
     * iteration absorbs anything the backend raises and moves on. CancellationException is rethrown
     * so cancelling the sweep still works, hence the suppression rather than a narrower catch.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun recoverPending(): List<PaymentSnapshot> {
        val recovered = mutableListOf<PaymentSnapshot>()
        for (pending in journal.unresolved()) {
            try {
                val snapshot = backend.status(pending.orderId)
                if (snapshot.status.isTerminal) {
                    journal.markResolved(pending.orderId, snapshot.status, snapshot.paymentId)
                    recovered += snapshot
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.w("Recovery failed for order=${pending.orderId}", t, tag = TAG)
            }
        }
        return recovered
    }

    data class PollConfig(
        val initialDelayMs: Long = 1_000,
        val maxDelayMs: Long = 8_000,
        val maxAttempts: Int = 5,
    )

    private companion object {
        const val TAG = "PaymentOrchestrator"
    }
}

@OptIn(ExperimentalTime::class)
private fun systemNowMs(): Long = Clock.System.now().toEpochMilliseconds()
