package com.paymentslab.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight

/**
 * The one status pill: a dot in the pure [tone] and a label on a 14% tint of it. The label is
 * darkened toward `onSurface` ([asLabelOn]) so it clears 4.5:1 in light and dark.
 */
@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = tone.color.copy(alpha = 0.14f),
        shape = CircleShape,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = DesignTokens.Spacing.md,
                    vertical = DesignTokens.Spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(DesignTokens.Spacing.sm)
                        .clip(CircleShape)
                        .background(tone.color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone.color.asLabelOn(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(start = DesignTokens.Spacing.sm),
            )
        }
    }
}
