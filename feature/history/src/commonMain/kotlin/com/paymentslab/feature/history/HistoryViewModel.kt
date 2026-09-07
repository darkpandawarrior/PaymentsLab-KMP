package com.paymentslab.feature.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.paymentslab.core.designsystem.format
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PendingPayment
import com.siddharth.kmp.paymentsapi.PendingPaymentJournal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row in the payment history, fully formatted for display. */
@Immutable
data class HistoryRow(
    val orderId: String,
    val catalogItemId: String,
    val gatewayId: String,
    val amount: String,
    val status: PaymentStatus,
    val createdAtEpochMs: Long,
)

/** Immutable state for [HistoryScreen]: the filtered payment history stream, newest first. */
@Immutable
data class HistoryUiState(
    val rows: ImmutableList<HistoryRow> = persistentListOf(),
    val isLoading: Boolean = true,
    val selectedStatuses: Set<PaymentStatus> = emptySet(),
)

/**
 * Collects the journal's full history stream and projects each [PendingPayment] into a display
 * [HistoryRow], newest first, filtered by [HistoryUiState.selectedStatuses] (empty = show all —
 * mirrors [com.paymentslab.feature.lab.LabHomeViewModel]'s status-filter shape).
 */
class HistoryViewModel(
    private val journal: PendingPaymentJournal,
) : StateViewModel<HistoryUiState>(HistoryUiState()) {
    val uiState: StateFlow<HistoryUiState> get() = state

    private var allPayments: List<PendingPayment> = emptyList()

    init {
        viewModelScope.launch {
            journal.observeAll().collect { payments ->
                allPayments = payments
                recompute()
            }
        }
    }

    fun onToggleStatusFilter(status: PaymentStatus) {
        val current = currentState.selectedStatuses
        recompute(selected = if (status in current) current - status else current + status)
    }

    // Takes `selected` explicitly (rather than always reading it back from `currentState`) so a
    // toggle writes state exactly once — folding the selection change and the row recompute into
    // the same assignment avoids a second, separate emission per toggle.
    private fun recompute(selected: Set<PaymentStatus> = currentState.selectedStatuses) {
        val filtered = if (selected.isEmpty()) allPayments else allPayments.filter { it.status in selected }
        setState {
            copy(
                rows =
                    filtered
                        .sortedByDescending { it.createdAtEpochMs }
                        .map { it.toRow() }
                        .toImmutableList(),
                isLoading = false,
                selectedStatuses = selected,
            )
        }
    }

    private companion object {
        const val TAG = "HistoryViewModel"
    }
}

private fun PendingPayment.toRow(): HistoryRow =
    HistoryRow(
        orderId = orderId,
        catalogItemId = catalogItemId,
        gatewayId = gatewayId.value,
        amount = amount.format(),
        status = status,
        createdAtEpochMs = createdAtEpochMs,
    )
