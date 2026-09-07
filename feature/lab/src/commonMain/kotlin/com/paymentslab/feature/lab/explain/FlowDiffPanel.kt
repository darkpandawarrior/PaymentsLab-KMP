package com.paymentslab.feature.lab.explain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.SectionHeader

/**
 * Renders [FlowDiffExplainer]'s two tiers for one [diff]: the deterministic summary first (always
 * present, appears instantly), then the model's streamed narrative underneath once it arrives.
 * Same shape as [ExplainerPanel] — [explainer] is `null` on a platform with no AI seam wired, and
 * the deterministic line is then the whole panel, not a degraded state.
 */
@Composable
fun FlowDiffPanel(
    diff: FlowDiff,
    explainer: FlowDiffExplainer?,
    modifier: Modifier = Modifier,
) {
    val deterministic = remember(diff) { diff.deterministicSummary() }
    var modelText by remember(diff) { mutableStateOf<String?>(null) }
    var streamDone by remember(diff) { mutableStateOf(false) }

    LaunchedEffect(diff, explainer) {
        modelText = null
        streamDone = false
        explainer?.streamNarrative(diff)?.collect { update ->
            when (update) {
                is ModelExplanation.Partial -> modelText = update.textSoFar
                is ModelExplanation.Failed -> Unit // deterministic summary already stands on its own
            }
        }
        streamDone = true
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.card),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            SectionHeader(text = "How these flows differ")
            Text(
                text = deterministic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                modelText != null ->
                    Text(
                        text = modelText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                // ponytail: silent on a model failure, same rationale as ExplainerPanel — the
                // deterministic summary above already stands on its own.
                explainer != null && !streamDone ->
                    Text(
                        text = "Asking the model for more detail…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}
