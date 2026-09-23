package com.paymentslab.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.siddharth.kmp.common.easeInQuart
import com.siddharth.kmp.common.easeOutBack
import com.siddharth.kmp.common.lerp

/**
 * A brief shield icon draw-in-and-settle, played once on mount. Visual reassurance that a
 * payment-bearing screen is protected — it does not itself do anything security-relevant (that's
 * `core:security`'s `SecureScreen`, an Android-only `FLAG_SECURE` mechanism this composable knows
 * nothing about); the two are used together on Android screens, and this one alone on iOS.
 *
 * Timeline for internal progress 0->1 (mirrors Gaddi's RubberStamp phase split):
 *   Phase A (0.00–PhaseAEnd): icon descends from InitialScale via EaseInQuart.
 *   Phase B (PhaseAEnd–PhaseBEnd): overshoot to PressScale via linear snap (unedged, for quick impulse).
 *   Phase C (PhaseBEnd–1.00): settle to 1.0x via EaseOutBack.
 *   Alpha reaches full opacity at AlphaCompletionProgress (40%), before phase A settles, so the icon
 *   shows up partway through its descent rather than popping in at the very end.
 */
@Composable
fun ShieldPulse(modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            progress.animateTo(1f, tween(DesignTokens.Motion.MEDIUM_MS + 200, easing = LinearEasing))
        }
    }

    val p = progress.value
    val scale =
        when {
            p < PhaseAEnd -> lerp(InitialScale, 1.0f, easeInQuart(p / PhaseAEnd))
            p < PhaseBEnd -> lerp(1.0f, PressScale, (p - PhaseAEnd) / PhaseBDuration)
            else -> lerp(PressScale, 1.0f, easeOutBack((p - PhaseBEnd) / PhaseCDuration))
        }
    val alpha = (p / AlphaCompletionProgress).coerceAtMost(1f)

    Icon(
        imageVector = Icons.Filled.Shield,
        contentDescription = "This screen is protected",
        tint = MaterialTheme.colorScheme.secondary,
        modifier =
            modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
    )
}

// Phase timeline constants for ShieldPulse animation
private const val PhaseAEnd = 0.55f
private const val PhaseBEnd = 0.78f
private const val PhaseBDuration = 0.23f // PhaseBEnd - PhaseAEnd
private const val PhaseCDuration = 0.22f // 1.0f - PhaseBEnd
private const val InitialScale = 1.6f
private const val PressScale = 0.92f
private const val AlphaCompletionProgress = 0.4f
