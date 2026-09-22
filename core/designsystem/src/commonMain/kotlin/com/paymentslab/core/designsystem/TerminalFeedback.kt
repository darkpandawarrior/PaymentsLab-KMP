package com.paymentslab.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A settled-success check mark: scales in from nothing, the terminal beat of the payment timeline. */
@Composable
fun SuccessBurst(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val scale = remember { Animatable(0f) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            scale.snapTo(1f)
        } else {
            scale.animateTo(1f, tween(DesignTokens.Motion.MEDIUM_MS, easing = DesignTokens.Motion.standardEasing))
        }
    }
    Box(
        modifier =
            modifier
                .size(size)
                .scale(scale.value)
                .clip(CircleShape)
                .background(StatusColors.Success),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** A settled-failure shake: three quick horizontal jitters, the terminal beat for a declined payment. */
@Composable
fun FailureShake(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val offsetX = remember { Animatable(0f) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            repeat(3) {
                offsetX.animateTo(ShakeAmplitudeDp, tween(ShakeStepMs))
                offsetX.animateTo(-ShakeAmplitudeDp, tween(ShakeStepMs))
            }
            offsetX.animateTo(0f, tween(ShakeStepMs))
        }
    }
    Box(
        modifier =
            modifier
                .offset(x = offsetX.value.dp)
                .size(size)
                .clip(CircleShape)
                .background(StatusColors.Danger.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = StatusColors.Danger,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private const val ShakeStepMs = 60
private const val ShakeAmplitudeDp = 10f
