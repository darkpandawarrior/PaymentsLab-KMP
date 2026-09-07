package com.paymentslab.feature.lab.explain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.SectionHeader
import com.siddharth.kmp.paymentsapi.GatewayId
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

/**
 * Stateful entry point: resolves [FlowDiffViewModel] and — same degrade-gracefully pattern as
 * [ProviderLabRoot][com.paymentslab.feature.lab.ProviderLabRoot] — resolves [FlowDiffExplainer]
 * from the global Koin context rather than requiring it, so a composition root with no AI seam
 * wired (web preview, iOS today) still renders the deterministic diff.
 */
@Composable
fun FlowDiffRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlowDiffViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val explainer = remember { GlobalContext.getOrNull()?.getOrNull<FlowDiffExplainer>() }
    FlowDiffScreen(
        state = state,
        explainer = explainer,
        onSelectBaseline = viewModel::selectBaseline,
        onSelectCompared = viewModel::selectCompared,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless "compare two gateways' recorded flows" screen: pick a baseline and a compared gateway
 * out of whatever [FlowDiffUiState.available] has recorded so far (run each provider at least once
 * in the live lab first), then [FlowDiffPanel] renders the diff once both are picked.
 */
@Composable
fun FlowDiffScreen(
    state: FlowDiffUiState,
    onSelectBaseline: (GatewayId) -> Unit,
    onSelectCompared: (GatewayId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    explainer: FlowDiffExplainer? = null,
) {
    LabScaffold(title = "Compare flows", onBack = onBack) { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            if (state.available.size < 2) {
                Text(
                    text =
                        "Run at least two providers in the lab first — comparing a flow needs two " +
                            "recorded runs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@LabScaffold
            }

            SectionHeader(text = "Baseline")
            GatewayPicker(
                options = state.available,
                selected = state.baseline,
                onSelect = onSelectBaseline,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(text = "Compared")
            GatewayPicker(
                options = state.available,
                selected = state.compared,
                onSelect = onSelectCompared,
                modifier = Modifier.fillMaxWidth(),
            )

            state.diff?.let { diff ->
                FlowDiffPanel(diff = diff, explainer = explainer, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GatewayPicker(
    options: ImmutableList<GatewayId>,
    selected: GatewayId?,
    onSelect: (GatewayId) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        options.forEach { id ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(id.value) },
            )
        }
    }
}
