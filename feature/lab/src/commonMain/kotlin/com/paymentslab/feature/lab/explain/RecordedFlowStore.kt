package com.paymentslab.feature.lab.explain

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory record of each provider's most recently completed [PaymentStep] trace, keyed by
 * [GatewayId] — the source [FlowDiffViewModel] draws "recorded flows" from to let a user compare
 * two providers. [ProviderLabViewModel][com.paymentslab.feature.lab.ProviderLabViewModel] writes
 * into this every time a run settles; only the latest trace per gateway is kept, which is all a
 * structural comparison (see [FlowTrace]) needs.
 *
 * // ponytail: process-lifetime, unpersisted, single Koin `single` shared by every screen — the Lab
 * // has no need to compare a flow recorded in a previous app session, so there is nothing to persist.
 */
class RecordedFlowStore {
    private val _traces = MutableStateFlow<Map<GatewayId, List<PaymentStep>>>(emptyMap())
    val traces: StateFlow<Map<GatewayId, List<PaymentStep>>> get() = _traces

    fun record(
        gatewayId: GatewayId,
        steps: List<PaymentStep>,
    ) {
        _traces.update { it + (gatewayId to steps) }
    }
}
