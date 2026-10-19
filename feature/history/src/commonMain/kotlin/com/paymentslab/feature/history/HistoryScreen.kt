package com.paymentslab.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.StatusPill
import com.paymentslab.core.designsystem.StatusTone
import com.siddharth.kmp.paymentsapi.PaymentStatus
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: resolves the ViewModel and renders the stateless [HistoryScreen]. */
@Composable
fun HistoryRoot(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onToggleStatusFilter = viewModel::onToggleStatusFilter,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless payment history: status filter chips, a list of rows, or an empty state. */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onToggleStatusFilter: (PaymentStatus) -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LabScaffold(title = "History", onBack = onBack) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            StatusFilterChips(
                selected = state.selectedStatuses,
                onToggle = onToggleStatusFilter,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.lg, vertical = DesignTokens.Spacing.sm),
            )
            if (!state.isLoading && state.rows.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = DesignTokens.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
                ) {
                    items(state.rows, key = { it.orderId }) { row ->
                        HistoryCard(row = row)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterChips(
    selected: Set<PaymentStatus>,
    onToggle: (PaymentStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        PaymentStatus.entries.forEach { status ->
            FilterChip(
                selected = status in selected,
                onClick = { onToggle(status) },
                label = { Text(status.name) },
            )
        }
    }
}

@Composable
private fun HistoryCard(
    row: HistoryRow,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.card),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.catalogItemId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusPill(label = row.status.name, tone = statusTone(row.status))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${row.gatewayId} · ${row.orderId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.amount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            modifier = Modifier.padding(DesignTokens.Spacing.xl),
        ) {
            Text(
                text = "No payments yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Run a payment in the Integration Lab and it will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps a payment status to the design system's shared [StatusTone]; no colour lives here. */
private fun statusTone(status: PaymentStatus): StatusTone =
    when (status) {
        PaymentStatus.SUCCESS, PaymentStatus.REFUNDED -> StatusTone.Success
        PaymentStatus.FAILED, PaymentStatus.CANCELLED -> StatusTone.Danger
        PaymentStatus.PENDING -> StatusTone.Warning
        PaymentStatus.CREATED -> StatusTone.Neutral
    }
