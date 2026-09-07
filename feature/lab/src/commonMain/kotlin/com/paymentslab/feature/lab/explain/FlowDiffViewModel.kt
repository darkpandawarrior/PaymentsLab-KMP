package com.paymentslab.feature.lab.explain

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State for [FlowDiffScreen]: every gateway with a recorded trace, the user's baseline/compared
 * picks, and the resulting [FlowDiff] once both are chosen (null while a pick is missing, the two
 * picks are the same gateway, or the store hasn't recorded that trace yet).
 */
@Immutable
data class FlowDiffUiState(
    val available: ImmutableList<GatewayId> = persistentListOf(),
    val baseline: GatewayId? = null,
    val compared: GatewayId? = null,
    val diff: FlowDiff? = null,
)

/**
 * Drives [FlowDiffScreen] from [RecordedFlowStore]: tracks which gateways have a recorded flow to
 * compare, and recomputes [FlowDiff.compare] whenever the user's baseline/compared pick — or the
 * store itself — changes (a run finishing while this screen is open updates the trace live).
 */
class FlowDiffViewModel(
    private val store: RecordedFlowStore,
) : StateViewModel<FlowDiffUiState>(FlowDiffUiState()) {
    val uiState: StateFlow<FlowDiffUiState> get() = state

    init {
        viewModelScope.launch {
            store.traces.collect { traces ->
                setState { withTraces(traces) }
            }
        }
    }

    fun selectBaseline(id: GatewayId) {
        val traces = store.traces.value
        setState { copy(baseline = id).withTraces(traces) }
    }

    fun selectCompared(id: GatewayId) {
        val traces = store.traces.value
        setState { copy(compared = id).withTraces(traces) }
    }

    private fun FlowDiffUiState.withTraces(traces: Map<GatewayId, List<PaymentStep>>): FlowDiffUiState =
        copy(
            available = traces.keys.toImmutableList(),
            diff = computeDiff(baseline, compared, traces),
        )

    private fun computeDiff(
        baseline: GatewayId?,
        compared: GatewayId?,
        traces: Map<GatewayId, List<PaymentStep>>,
    ): FlowDiff? {
        if (baseline == null || compared == null || baseline == compared) return null
        val baselineTrace = traces[baseline] ?: return null
        val comparedTrace = traces[compared] ?: return null
        return FlowDiff.compare(
            FlowTrace.from(baseline, baselineTrace),
            FlowTrace.from(compared, comparedTrace),
        )
    }
}
