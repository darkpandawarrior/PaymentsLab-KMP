package com.paymentslab.feature.lab

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.paymentslab.core.designsystem.ClientResultCopy
import com.paymentslab.core.designsystem.ErroredCopy
import com.paymentslab.core.designsystem.FlowHop
import com.paymentslab.core.designsystem.LaunchingCopy
import com.paymentslab.core.designsystem.OrderCreatedCopy
import com.paymentslab.core.designsystem.SettledCopy
import com.paymentslab.core.designsystem.TimelineCopy
import com.paymentslab.core.designsystem.VerifyingCopy
import com.paymentslab.core.designsystem.toTimelineStep
import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.paymentslab.feature.lab.explain.GatewayFailure
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.designsystem.TimelineStep
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.GatewayId
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The Lab's technical, dev-facing wording for the shared timeline mapping. */
private val LabTimelineCopy =
    TimelineCopy(
        orderCreated =
            OrderCreatedCopy(
                title = "Order created",
                subtitle = { amount -> "Server resolved the price · $amount" },
            ),
        launching =
            LaunchingCopy(
                title = { gatewayId -> "Launching $gatewayId" },
                subtitle = "Journal row written before the SDK opens",
            ),
        clientResult =
            ClientResultCopy(
                title = "Client result",
                successSubtitle = "SDK reported success (unverified)",
                failureSubtitle = { code -> "SDK reported failure: $code" },
                pendingSubtitle = { reason -> "SDK reported pending: $reason" },
                cancelledSubtitle = "User cancelled",
            ),
        verifying =
            VerifyingCopy(
                title = "Verifying",
                subtitle = "Confirming against the server — a client success is only a hint",
            ),
        settled =
            SettledCopy(
                title = "Settled",
                subtitle = { statusName -> "Server-authoritative: $statusName" },
            ),
        errored =
            ErroredCopy(
                title = "Error",
                fallbackSubtitle = "The flow broke before settling",
            ),
    )

/**
 * State for [ProviderLabScreen]: the live timeline built from the orchestrator's [PaymentStep]
 * stream, whether a run is in flight, and the terminal server status once settled.
 */
@Immutable
data class ProviderLabUiState(
    val steps: ImmutableList<TimelineStep> = persistentListOf(),
    val isRunning: Boolean = false,
    val finalStatus: PaymentStatus? = null,
    /** The last client-reported gateway decline this run saw, if any — grounds [ExplainerPanel][com.paymentslab.feature.lab.explain.ExplainerPanel]. */
    val gatewayFailure: GatewayFailure? = null,
    val hasRun: Boolean = false,
    /** Where the [PaymentFlowDiagram] packet currently sits — null until the first step lands. */
    val currentHop: FlowHop? = null,
    /** Whether [currentHop] has been backend-confirmed yet, vs. still just a client hint. */
    val verified: Boolean = false,
    /**
     * Idempotency key for the current order attempt. catalogItemId/gatewayId are nav params (fixed
     * for this screen), so an attempt = "keep pressing Run until it succeeds": the key survives
     * "Run again" and only resets after a terminal SUCCESS (next run is a genuinely new order).
     */
    val idempotencyKey: String? = null,
)

/**
 * Drives one provider's live payment through [PaymentFlowRunner] and folds the emitted
 * [PaymentStep]s into a growing [TimelineStep] list. While the flow is in flight the trailing step
 * is shown ACTIVE (pulsing); when a terminal step lands, the trailing step takes its own terminal
 * colour. Concurrent runs are guarded by [ProviderLabUiState.isRunning] — a second [start] while one
 * is running is ignored. "Run again" is [start] itself, which resets the timeline first.
 */
class ProviderLabViewModel(
    private val flowRunner: PaymentFlowRunner,
) : StateViewModel<ProviderLabUiState>(ProviderLabUiState()) {
    val uiState: StateFlow<ProviderLabUiState> get() = state

    @OptIn(ExperimentalUuidApi::class)
    fun start(
        host: PaymentHost,
        gatewayId: GatewayId,
        catalogItemId: String,
    ) {
        if (currentState.isRunning) return
        // Carry the current attempt's key across a "Run again"; mint one on the first run of an attempt.
        val idempotencyKey = currentState.idempotencyKey ?: Uuid.random().toString()
        setState { ProviderLabUiState(isRunning = true, hasRun = true, idempotencyKey = idempotencyKey) }

        viewModelScope.launch {
            val accumulated = mutableListOf<PaymentStep>()
            try {
                flowRunner.run(host, gatewayId, catalogItemId, idempotencyKey).collect { step ->
                    accumulated += step
                    setState {
                        copy(
                            steps = accumulated.toTimeline(runInFlight = true),
                            currentHop = step.toFlowHop(),
                            verified = step.isVerified(),
                        )
                    }
                }
                val terminal = accumulated.terminalStatus()
                setState {
                    copy(
                        steps = accumulated.toTimeline(runInFlight = false),
                        isRunning = false,
                        finalStatus = terminal,
                        gatewayFailure = accumulated.lastGatewayFailure(gatewayId, terminal),
                        // Clear on success (next run = new order); keep on failure so "Run again" retries
                        // the SAME order attempt and the server dedups it.
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

    /**
     * Build the timeline from accumulated steps. While the run is in flight the last non-terminal
     * step pulses ACTIVE; once the flow completes every step keeps its mapped state.
     */
    private fun List<PaymentStep>.toTimeline(runInFlight: Boolean): ImmutableList<TimelineStep> {
        val mapped = map { it.toTimelineStep(LabTimelineCopy) }.toMutableList()
        if (runInFlight && mapped.isNotEmpty()) {
            val lastIndex = mapped.lastIndex
            val last = mapped[lastIndex]
            // Only in-flight (non-terminal) steps get promoted to ACTIVE; a terminal step keeps its colour.
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

    /**
     * The last client SDK decline this run saw, for [ErrorExplainer][com.paymentslab.feature.lab.explain.ErrorExplainer]
     * to explain. Grounded per [PaymentFsm][com.paymentslab.core.orchestration.fsm]: a client
     * [PaymentResult.Failure] always sends the FSM into [PaymentPhase.VERIFYING] (the server is
     * asked to confirm it — see `PaymentReducer.onClientResult`); once the run's own [terminal]
     * status actually settles as [PaymentStatus.FAILED], that decline is server-confirmed, so the
     * grounding is upgraded to [PaymentPhase.TERMINAL].
     *
     * // ponytail: only covers a *client-reported* decline. A payment that fails purely server-side
     * // (polling settles FAILED with no client Failure ever seen) has no FailureCode/raw payload to
     * // explain and is deliberately left out — add a PaymentSnapshot-based path if that case turns
     * // out to need one too.
     */
    private fun List<PaymentStep>.lastGatewayFailure(
        gatewayId: GatewayId,
        terminal: PaymentStatus?,
    ): GatewayFailure? {
        val clientFailure =
            filterIsInstance<PaymentStep.ClientResult>().lastOrNull { it.result is PaymentResult.Failure }
                ?: return null
        val result = clientFailure.result as PaymentResult.Failure
        val phase = if (terminal == PaymentStatus.FAILED) PaymentPhase.TERMINAL else PaymentPhase.VERIFYING
        return GatewayFailure(gatewayId = gatewayId, code = result.code, payload = clientFailure.payload, phase = phase)
    }

    private companion object {
        const val TAG = "ProviderLabViewModel"
    }
}
