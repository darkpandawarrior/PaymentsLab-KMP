package com.paymentslab.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Semantic status tones shared by [StepTimeline] and [GatewayStatusBadge].
 *
 * These are theme-independent so previews and non-composable code can reach them; they are
 * tuned to read AA on both the light and dark PaymentsLab-KMP surfaces.
 */
internal object StatusColors {
    val Success = Color(0xFF1E9E6A) // settlement done / sandbox ready
    val Warning = Color(0xFFB57900) // KYC gated / attention
    val Danger = Color(0xFFCE3B3B) // failed / error
    val Neutral = Color(0xFF8A8894) // pending / coming soon
    val Info = Color(0xFF3B6FCE) // mock mode — real code, simulated end-to-end
}

/** Public handle on [StatusColors] so feature modules pick a tone without touching a hex. */
enum class StatusTone(
    internal val color: Color,
) {
    Success(StatusColors.Success),
    Warning(StatusColors.Warning),
    Danger(StatusColors.Danger),
    Neutral(StatusColors.Neutral),
    Info(StatusColors.Info),
}

/** Share of the surface's `onSurface` blended into a tone for label text; see [StatusPill]. */
private const val LabelBlend = 0.5f

/**
 * The tone shifted toward [onSurface] so small label text reaches WCAG 4.5:1 on its own 14% tint
 * in both schemes (the raw tones measure 2.8 to 4.6 light and 3.4 to 4.6 dark; at 0.5 the worst
 * pair is 5.4 light, 5.7 dark). The dot and tint keep the pure tone.
 */
internal fun Color.asLabelOn(onSurface: Color): Color = lerp(this, onSurface, LabelBlend)
