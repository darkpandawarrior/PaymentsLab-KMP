package com.paymentslab.feature.checkoutdemo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.paymentslab.core.designsystem.ClientResultCopy
import com.paymentslab.core.designsystem.ErroredCopy
import com.paymentslab.core.designsystem.LaunchingCopy
import com.paymentslab.core.designsystem.OrderCreatedCopy
import com.paymentslab.core.designsystem.SettledCopy
import com.paymentslab.core.designsystem.TimelineCopy
import com.paymentslab.core.designsystem.VerifyingCopy
import com.paymentslab.core.designsystem.format
import com.paymentslab.core.designsystem.toTimelineStep
import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.designsystem.TimelineStep
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The checkout demo's friendlier, consumer-facing wording for the shared timeline mapping. */
private val CheckoutTimelineCopy =
    TimelineCopy(
        orderCreated =
            OrderCreatedCopy(
                title = "Order confirmed",
                subtitle = { amount -> "We asked the server for the price — $amount" },
            ),
        launching =
            LaunchingCopy(
                title = { gatewayId -> "Opening $gatewayId" },
                subtitle = "Saved a recovery note before opening the payment sheet",
            ),
        clientResult =
            ClientResultCopy(
                title = "Payment sheet returned",
                successSubtitle = "Reported success (not yet verified)",
                failureSubtitle = { code -> "Reported failure: $code" },
                pendingSubtitle = { reason -> "Reported pending: $reason" },
                cancelledSubtitle = "You cancelled the payment",
            ),
        verifying =
            VerifyingCopy(
                title = "Double-checking with the server",
                subtitle = "A success on the phone is only trusted once the server agrees",
            ),
        settled =
            SettledCopy(
                title = "Done",
                subtitle = { statusName -> "Server says: $statusName" },
            ),
        errored =
            ErroredCopy(
                title = "Something went wrong",
                fallbackSubtitle = "The checkout could not complete",
            ),
    )

/** A gateway the demo can actually run — by default only SANDBOX_READY providers are offered. */
@Immutable
data class CheckoutGateway(
    val id: GatewayId,
    val displayName: String,
)

/** How a finished checkout run ended, decided only from the server-confirmed state (see [CheckoutResult]). */
enum class CheckoutOutcome { SUCCESS, FAILURE, PENDING_VERIFICATION }

/**
 * The terminal result the full-screen result page renders. It exists only once the run has a
 * server-side answer ([PaymentStep.Settled]) or has provably failed; a client SDK "success" alone
 * never produces [CheckoutOutcome.SUCCESS]. If the SDK reported success or pending but the server
 * could not be reached, the outcome is [CheckoutOutcome.PENDING_VERIFICATION]: the money may have
 * moved, so we must not claim failure either.
 */
@Immutable
data class CheckoutResult(
    val outcome: CheckoutOutcome,
    val amountLabel: String,
    val gatewayName: String,
    val orderId: String?,
    val paymentId: String?,
    val reason: String?,
    val completedAtMs: Long,
)

/** State for [CheckoutScreen]: the selected product + gateway, the live mini-timeline, the outcome. */
@Immutable
data class CheckoutUiState(
    val products: ImmutableList<DemoProduct> = DEMO_PRODUCTS,
    val gateways: ImmutableList<CheckoutGateway> = persistentListOf(),
    val selectedProduct: DemoProduct? = null,
    val selectedGatewayId: GatewayId? = null,
    val steps: ImmutableList<TimelineStep> = persistentListOf(),
    val isRunning: Boolean = false,
    val finalStatus: PaymentStatus? = null,
    /** Non-null only after server-side confirmation; drives the full-screen result page. */
    val result: CheckoutResult? = null,
    /**
     * Idempotency key for the current order attempt. Stable across re-presses of Pay (so a retry
     * after an ambiguous failure dedups server-side); reset to null on success or a changed
     * selection so the NEXT press mints a fresh key for a genuinely new order.
     */
    val idempotencyKey: String? = null,
) {
    /** Pay is enabled only with a product, a gateway, and no run in flight. */
    val canPay: Boolean get() = selectedProduct != null && selectedGatewayId != null && !isRunning
}

/**
 * The explained-checkout demo. Picks a demo product and a SANDBOX_READY gateway, runs the same
 * [PaymentFlowRunner] flow the Lab uses, and folds the [PaymentStep]s into a friendly mini-timeline
 * so the checkout doubles as the explainer. Concurrent runs are guarded by [CheckoutUiState.isRunning].
 */
class CheckoutViewModel(
    private val flowRunner: PaymentFlowRunner,
    registry: PaymentGatewayRegistry,
    // SANDBOX_READY by default (real sandbox keys); the web preview widens to MOCK_MODE, where the
    // whole fleet is simulated anyway.
    offeredStatuses: Set<GatewayStatus> = setOf(GatewayStatus.SANDBOX_READY),
) : StateViewModel<CheckoutUiState>(
        CheckoutUiState(
            gateways =
                registry.gateways
                    .filter { it.meta.status in offeredStatuses }
                    .map { CheckoutGateway(it.id, it.meta.displayName) }
                    .toImmutableList(),
        ),
    ) {
    val uiState: StateFlow<CheckoutUiState> get() = state

    fun selectProduct(product: DemoProduct) {
        if (currentState.isRunning) return
        // A different selection is a different order — drop the key so the next Pay mints a fresh one.
        setState { copy(selectedProduct = product, idempotencyKey = null) }
    }

    fun selectGateway(gatewayId: GatewayId) {
        if (currentState.isRunning) return
        setState { copy(selectedGatewayId = gatewayId, idempotencyKey = null) }
    }

    /** Leaves the result page for the picker with the selection intact (Change method, Done, Retry). */
    fun dismissResult() {
        if (currentState.isRunning) return
        setState { copy(result = null, steps = persistentListOf(), finalStatus = null) }
    }

    // The UI boundary: anything escaping here cancels the viewModelScope and the screen freezes
    // mid-run, so the catch is total by design. The flow reaches into gateway SDKs whose throw
    // surface this module does not own. CancellationException is rethrown first.
    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalUuidApi::class)
    fun pay(host: PaymentHost) {
        val current = currentState
        if (!current.canPay) return
        val product = current.selectedProduct ?: return
        val gatewayId = current.selectedGatewayId ?: return

        // Reuse the current attempt's key (re-press after a failure = same order); mint one if none.
        val idempotencyKey = current.idempotencyKey ?: Uuid.random().toString()

        setState {
            copy(
                isRunning = true,
                steps = persistentListOf(),
                finalStatus = null,
                result = null,
                idempotencyKey = idempotencyKey,
            )
        }

        viewModelScope.launch {
            val accumulated = mutableListOf<PaymentStep>()
            try {
                flowRunner.run(host, gatewayId, product.catalogItemId, idempotencyKey).collect { step ->
                    accumulated += step
                    setState { copy(steps = accumulated.toTimeline(runInFlight = true)) }
                }
                val result = accumulated.toResult(current)
                val terminal = accumulated.terminalStatus()
                setState {
                    copy(
                        steps = accumulated.toTimeline(runInFlight = false),
                        isRunning = false,
                        finalStatus = terminal,
                        result = result,
                        // Success = a genuinely new order next time; clear the key. A failure keeps it
                        // so re-pressing Pay retries the SAME order attempt (server dedups).
                        idempotencyKey = if (terminal == PaymentStatus.SUCCESS) null else idempotencyKey,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Was `setState { copy(isRunning = false) }` and nothing else: a crashed run left the
                // UI with the spinner off, a half-drawn timeline and no failure anywhere on screen,
                // and the cause never reached a log. Report it as a terminal failure and log the cause.
                AppLog.e("Checkout run failed for gateway=${current.selectedGatewayId?.value}", t, tag = Tag)
                val result = accumulated.toResult(current)
                setState { copy(isRunning = false, finalStatus = result.outcome.toStatus(), result = result) }
            }
        }
    }

    private fun List<PaymentStep>.toTimeline(runInFlight: Boolean): ImmutableList<TimelineStep> {
        val mapped = map { it.toTimelineStep(CheckoutTimelineCopy) }.toMutableList()
        if (runInFlight && mapped.isNotEmpty()) {
            val lastIndex = mapped.lastIndex
            val last = mapped[lastIndex]
            if (last.state == StepState.DONE) {
                mapped[lastIndex] = last.copy(state = StepState.ACTIVE)
            }
        }
        return mapped.toImmutableList()
    }

    private fun List<PaymentStep>.terminalStatus(): PaymentStatus? =
        when (val last = lastOrNull()) {
            is PaymentStep.Settled -> last.status
            is PaymentStep.Errored -> if (sdkMayHaveCharged()) PaymentStatus.PENDING else PaymentStatus.FAILED
            else -> null
        }

    /** The SDK said success or pending: the customer may have been charged even if the server is unreachable. */
    private fun List<PaymentStep>.sdkMayHaveCharged(): Boolean =
        filterIsInstance<PaymentStep.ClientResult>().any {
            it.result is PaymentResult.Success || it.result is PaymentResult.Pending
        }

    private fun CheckoutOutcome.toStatus(): PaymentStatus =
        when (this) {
            CheckoutOutcome.SUCCESS -> PaymentStatus.SUCCESS
            CheckoutOutcome.FAILURE -> PaymentStatus.FAILED
            CheckoutOutcome.PENDING_VERIFICATION -> PaymentStatus.PENDING
        }

    /**
     * Folds the run into the terminal [CheckoutResult]. Server-confirmation gating lives here: only a
     * [PaymentStep.Settled] with SUCCESS/REFUNDED yields SUCCESS; a run that ended without a Settled
     * (error, crash, stream ended early) is FAILURE only if the SDK never claimed a charge.
     */
    private fun List<PaymentStep>.toResult(from: CheckoutUiState): CheckoutResult {
        val settled = lastOrNull() as? PaymentStep.Settled
        val order = filterIsInstance<PaymentStep.OrderCreated>().lastOrNull()
        val clientFailure = filterIsInstance<PaymentStep.ClientResult>().lastOrNull()?.result
        val errored = lastOrNull() as? PaymentStep.Errored
        val chargeUncertain = settled == null && sdkMayHaveCharged()

        val (outcome, reason) =
            when {
                settled != null ->
                    when (settled.status) {
                        PaymentStatus.SUCCESS, PaymentStatus.REFUNDED -> CheckoutOutcome.SUCCESS to null
                        PaymentStatus.PENDING, PaymentStatus.CREATED ->
                            CheckoutOutcome.PENDING_VERIFICATION to "The server has not confirmed this payment yet."
                        PaymentStatus.CANCELLED -> CheckoutOutcome.FAILURE to "You cancelled the payment."
                        PaymentStatus.FAILED -> CheckoutOutcome.FAILURE to failureReason(clientFailure, null)
                    }
                chargeUncertain ->
                    CheckoutOutcome.PENDING_VERIFICATION to
                        "The payment sheet reported a result, but we could not confirm it with the server."
                else -> CheckoutOutcome.FAILURE to failureReason(clientFailure, errored?.message)
            }

        val gatewayName = from.gateways.firstOrNull { it.id == from.selectedGatewayId }?.displayName ?: "—"
        return CheckoutResult(
            outcome = outcome,
            amountLabel = order?.amount?.format() ?: from.selectedProduct?.priceLabel.orEmpty(),
            gatewayName = gatewayName,
            orderId = settled?.snapshot?.orderId ?: order?.orderId,
            paymentId = settled?.snapshot?.paymentId,
            reason = reason,
            completedAtMs = systemNowMs(),
        )
    }

    private fun failureReason(
        clientResult: PaymentResult?,
        erroredMessage: UiText?,
    ): String =
        when {
            clientResult is PaymentResult.Failure -> clientResult.message.asText() ?: "The payment was declined."
            clientResult is PaymentResult.Cancelled -> "You cancelled the payment."
            erroredMessage != null -> erroredMessage.asText() ?: "The checkout could not complete."
            else -> "The checkout could not complete."
        }

    private fun UiText.asText(): String? = (this as? UiText.Dynamic)?.value

    private companion object {
        const val TAG = "CheckoutViewModel"
    }
}

private const val Tag = "CheckoutViewModel"

@OptIn(ExperimentalTime::class)
private fun systemNowMs(): Long = Clock.System.now().toEpochMilliseconds()
