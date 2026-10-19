package com.paymentslab.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * Counts up from zero to [amountMinor] on first composition (and again if the amount changes) — the
 * order-creation beat: money appearing is one of the few animations in a payments app that should
 * feel *alive* rather than merely decorative.
 */
@Composable
fun AnimatedAmount(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    val animated = remember { Animatable(0f) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(amountMinor) {
        if (reducedMotion) {
            animated.snapTo(amountMinor.toFloat())
        } else {
            animated.animateTo(
                amountMinor.toFloat(),
                tween(DesignTokens.Motion.MEDIUM_MS, easing = DesignTokens.Motion.standardEasing),
            )
        }
    }
    Text(text = formatMoney(animated.value.toLong(), currency), style = style, modifier = modifier)
}
