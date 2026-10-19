package com.paymentslab.feature.checkoutdemo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.PrimaryButton
import com.paymentslab.core.designsystem.SectionHeader
import com.paymentslab.core.designsystem.ShieldPulse
import com.siddharth.kmp.designsystem.StepTimeline
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentHost
import kotlinx.collections.immutable.ImmutableList

/**
 * Stateful entry point for the explained-checkout demo. [paymentHost] is supplied by the app (the
 * real `AndroidPaymentHost`); the screen never constructs it.
 */
@Composable
fun CheckoutRoot(
    paymentHost: PaymentHost,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel =
        org.koin.compose.viewmodel
            .koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CheckoutScreen(
        state = state,
        onSelectProduct = viewModel::selectProduct,
        onSelectGateway = viewModel::selectGateway,
        onPay = { viewModel.pay(paymentHost) },
        onBack = onBack,
        // Retry re-presses Pay: the VM keeps the idempotency key after a failure, so the server dedups.
        onRetry = { viewModel.pay(paymentHost) },
        onChangeMethod = viewModel::dismissResult,
        // Done leaves checkout entirely; popping the route drops this ViewModel, so no stale state.
        onDone = onBack ?: viewModel::dismissResult,
        modifier = modifier,
    )
}

/** Stateless explained checkout: pick a product, pick a gateway, pay, and watch the mini-timeline. */
@Composable
fun CheckoutScreen(
    state: CheckoutUiState,
    onSelectProduct: (DemoProduct) -> Unit,
    onSelectGateway: (GatewayId) -> Unit,
    onPay: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onChangeMethod: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    // The terminal page is shown only when the VM has a server-confirmed result (see CheckoutResult).
    state.result?.let { result ->
        CheckoutResultScreen(
            result = result,
            onDone = onDone,
            onRetry = onRetry,
            onChangeMethod = onChangeMethod,
            modifier = modifier,
        )
        return
    }
    LabScaffold(
        title = "Checkout Demo",
        onBack = onBack,
        bottomBar = { PayBar(state = state, onPay = onPay) },
    ) { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            ) {
                ShieldPulse()
                Text(
                    text = "Protected — screenshots and screen recording are blocked here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val inFlight = state.isRunning || state.steps.isNotEmpty()
            if (!inFlight) {
                SectionHeader(text = "1 · Pick a product")
                state.products.forEach { product ->
                    ProductRow(
                        product = product,
                        selected = product.catalogItemId == state.selectedProduct?.catalogItemId,
                        onClick = { onSelectProduct(product) },
                    )
                }

                SectionHeader(text = "2 · Pick a gateway")
                GatewayChips(
                    gateways = state.gateways,
                    selected = state.selectedGatewayId,
                    onSelect = onSelectGateway,
                )
            }

            state.selectedProduct?.let { product ->
                SectionHeader(text = "Order summary")
                CheckoutSummary(
                    product = product,
                    gateways = state.gateways,
                    selectedGatewayId = state.selectedGatewayId,
                )
            }

            if (inFlight) {
                SectionHeader(text = "What's happening")
                StepTimeline(steps = state.steps, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The Pay action, pinned under the scrolling content. It pads for the navigation bar and the IME
 * itself (the scaffold's bottomBar slot does not), and shows the formatted total so the amount is
 * visible without scrolling back to the summary.
 */
@Composable
internal fun PayBar(
    state: CheckoutUiState,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = DesignTokens.Elevation.raised,
    ) {
        Column(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = DesignTokens.Spacing.lg, vertical = DesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
        ) {
            PrimaryButton(
                text =
                    when {
                        state.isRunning -> "Paying…"
                        state.selectedProduct != null -> "Pay ${state.selectedProduct.priceLabel}"
                        else -> "Pay"
                    },
                onClick = onPay,
                enabled = state.canPay,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProductRow(
    product: DemoProduct,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = if (selected) DesignTokens.Elevation.floating else DesignTokens.Elevation.raised,
            ),
        colors =
            if (selected) {
                CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.elevatedCardColors()
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = product.priceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GatewayChips(
    gateways: ImmutableList<CheckoutGateway>,
    selected: GatewayId?,
    onSelect: (GatewayId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        gateways.forEach { gateway ->
            val isSelected = gateway.id == selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(DesignTokens.Radius.md),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(DesignTokens.Radius.md))
                        .border(
                            width = DesignTokens.Spacing.xs / 4,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(DesignTokens.Radius.md),
                        ).selectable(selected = isSelected, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(gateway.id)
                        },
            ) {
                Text(
                    text = gateway.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier.padding(
                            horizontal = DesignTokens.Spacing.md,
                            vertical = DesignTokens.Spacing.sm,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CheckoutSummary(
    product: DemoProduct,
    gateways: ImmutableList<CheckoutGateway>,
    selectedGatewayId: GatewayId?,
    modifier: Modifier = Modifier,
) {
    val gatewayName = gateways.firstOrNull { it.id == selectedGatewayId }?.displayName ?: "—"
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.card),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            SummaryLine(label = "Item", value = product.title)
            SummaryLine(label = "Gateway", value = gatewayName)
            SummaryLine(label = "Total", value = product.priceLabel, emphasise = true)
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasise: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasise) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
