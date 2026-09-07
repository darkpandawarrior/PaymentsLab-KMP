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
 * Renders [ErrorExplainer]'s two tiers for one [failure]: the deterministic explanation first
 * (always present, appears instantly), then the model's streamed elaboration underneath once it
 * arrives. [explainer] is `null` on a platform with no AI seam wired (the web preview, iOS today)
 * — the deterministic line is then the whole panel, which is the intended "no key, no network"
 * floor, not a degraded state.
 */
@Composable
fun ExplainerPanel(
    failure: GatewayFailure,
    explainer: ErrorExplainer?,
    modifier: Modifier = Modifier,
) {
    val deterministic = remember(failure) { explainer?.deterministic(failure) ?: failure.code.name }
    var modelText by remember(failure) { mutableStateOf<String?>(null) }
    // Flips once streamRicher's flow completes, however it completes (a Failed chunk, or the flow
    // simply ending with nothing at all — e.g. no provider wired for this platform) — distinguishes
    // "still asking" from "asked, nothing more to add" so the panel doesn't show a spinner forever.
    var streamDone by remember(failure) { mutableStateOf(false) }

    LaunchedEffect(failure, explainer) {
        modelText = null
        streamDone = false
        explainer?.streamRicher(failure, deterministic)?.collect { update ->
            when (update) {
                is ModelExplanation.Partial -> modelText = update.textSoFar
                is ModelExplanation.Failed -> Unit // deterministic line already stands on its own
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
            SectionHeader(text = "What this means")
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
                // ponytail: silent on a model failure (NoKey/Network/RateLimited/...) rather than
                // surfacing the raw AiFailure — the deterministic line above already stands on its
                // own; a dedicated per-reason message is only worth it if users ask why nothing more
                // showed up.
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
