package com.paymentslab.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PendingPayment
import com.siddharth.kmp.paymentsapi.PendingPaymentJournal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row in the "Recent activity" preview — a small subset of [HistoryRow]'s fields. */
@Immutable
data class RecentActivityRow(
    val orderId: String,
    val catalogItemId: String,
    val status: PaymentStatus,
)

/** Immutable state for [HomeScreen]. */
@Immutable
data class HomeUiState(
    val gatewayCount: Int = 0,
    val successRatePercent: Int = 0,
    val recentActivity: ImmutableList<RecentActivityRow> = persistentListOf(),
)

/**
 * Aggregates the registry (gateway count) and the payment journal (success rate + recent
 * activity) into Home's dashboard stats. The journal stream is hot and Room-backed, same as
 * [com.paymentslab.feature.history.HistoryViewModel] — this mirrors that pattern rather than
 * duplicating its stream, since Home needs a *different projection* of the same source data.
 */
class HomeViewModel(
    registry: PaymentGatewayRegistry,
    journal: PendingPaymentJournal,
) : StateViewModel<HomeUiState>(HomeUiState(gatewayCount = registry.gateways.size)) {
    val uiState: StateFlow<HomeUiState> get() = state

    init {
        viewModelScope.launch {
            journal.observeAll().collect { payments ->
                setState {
                    copy(
                        successRatePercent = payments.successRatePercent(),
                        recentActivity =
                            payments
                                .sortedByDescending { it.createdAtEpochMs }
                                .take(3)
                                .map { RecentActivityRow(it.orderId, it.catalogItemId, it.status) }
                                .toImmutableList(),
                    )
                }
            }
        }
    }
}

/** Terminal (resolved) statuses count toward the rate; [PaymentStatus.CREATED] is still in-flight. */
private fun List<PendingPayment>.successRatePercent(): Int {
    val resolved = filter { it.status != PaymentStatus.CREATED }
    if (resolved.isEmpty()) return 0
    val successes = resolved.count { it.status == PaymentStatus.SUCCESS || it.status == PaymentStatus.REFUNDED }
    return ((successes.toDouble() / resolved.size) * 100).let { kotlin.math.round(it).toInt() }
}
