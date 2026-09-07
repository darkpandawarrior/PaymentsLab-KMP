package com.paymentslab.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.LocalReducedMotion
import com.paymentslab.core.designsystem.PaymentsLabHeroGradient
import com.siddharth.kmp.designsystem.AnimatedCounter
import com.siddharth.kmp.paymentsapi.PaymentStatus
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: resolves the ViewModel and hands its state to the stateless [HomeScreen]. */
@Composable
fun HomeRoot(
    onOpenExplore: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onOpenExplore = onOpenExplore,
        onOpenActivity = onOpenActivity,
        modifier = modifier,
    )
}

/** Stateless Home dashboard: hero stat card, an "Explore gateways" quick action, recent activity. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenExplore: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabScaffold(title = "PaymentsLab-KMP") { padding ->
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            item { HeroStatCard(state = state, onOpenExplore = onOpenExplore) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onOpenActivity) { Text("View all") }
                }
            }
            if (state.recentActivity.isEmpty()) {
                item {
                    Text(
                        text = "Run a payment in Explore and it'll show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = DesignTokens.Spacing.lg),
                    )
                }
            } else {
                items(state.recentActivity, key = { it.orderId }) { row ->
                    RecentActivityRowCard(row = row)
                }
            }
        }
    }
}

@Composable
private fun HeroStatCard(
    state: HomeUiState,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DesignTokens.Radius.lg)),
        tonalElevation = DesignTokens.Elevation.floating,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PaymentsLabHeroGradient)
                    .padding(DesignTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xl),
            ) {
                Column {
                    AnimatedCounter(
                        target = state.gatewayCount,
                        style = MaterialTheme.typography.displaySmall.copy(color = Color.White),
                        reducedMotion = LocalReducedMotion.current,
                    )
                    Text(
                        text = "gateways integrated",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Column {
                    AnimatedCounter(
                        target = state.successRatePercent,
                        suffix = "%",
                        style = MaterialTheme.typography.displaySmall.copy(color = Color.White),
                        reducedMotion = LocalReducedMotion.current,
                    )
                    Text(
                        text = "success rate",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            TextButton(onClick = onOpenExplore) {
                Text("Explore gateways →", color = Color.White)
            }
        }
    }
}

@Composable
private fun RecentActivityRowCard(
    row: RecentActivityRow,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.raised),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.catalogItemId, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.status.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (row.status == PaymentStatus.SUCCESS || row.status == PaymentStatus.REFUNDED) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
