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
import com.paymentslab.core.designsystem.toTimelineStep
import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.designsystem.TimelineStep
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
                val terminal = accumulated.terminalStatus()
                setState {
                    copy(
                        steps = accumulated.toTimeline(runInFlight = false),
                        isRunning = false,
                        finalStatus = terminal,
                        // Success = a genuinely new order next time; clear the key. A failure keeps it
                        // so re-pressing Pay retries the SAME order attempt (server dedups).
                        idempotencyKey = if (terminal == PaymentStatus.SUCCESS) null else idempotencyKey,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                setState { copy(isRunning = false) }
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
            is PaymentStep.Errored -> PaymentStatus.FAILED
            else -> null
        }

    private companion object {
        const val TAG = "CheckoutViewModel"
    }
}
