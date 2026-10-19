package com.paymentslab.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which glyph a [TerminalMark] draws. */
enum class TerminalMarkKind(
    internal val tone: StatusTone,
) {
    Success(StatusTone.Success),
    Failure(StatusTone.Danger),
    Pending(StatusTone.Warning),
}

/**
 * A Canvas-drawn terminal mark for the full-screen checkout result: a filled disc, then the glyph
 * (check, cross, or clock hands) stroked on over [DesignTokens.Motion.MEDIUM_MS]. Under
 * [LocalReducedMotion] it starts on its final frame, so nothing moves and a screenshot is stable.
 */
@Composable
fun TerminalMark(
    kind: TerminalMarkKind,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            progress.animateTo(1f, tween(DesignTokens.Motion.LONG_MS, easing = DesignTokens.Motion.standardEasing))
        }
    }
    val fill = kind.tone.color
    Canvas(modifier = modifier.size(size)) {
        val p = progress.value
        val discP = (p / DiscShare).coerceIn(0f, 1f)
        val glyphP = ((p - DiscShare) / (1f - DiscShare)).coerceIn(0f, 1f)
        drawCircle(color = fill, radius = this.size.minDimension / 2f * discP)
        val strokes = glyphStrokes(kind, this.size.minDimension)
        strokes.forEachIndexed { i, stroke ->
            val local = (glyphP * strokes.size - i).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            val measure = PathMeasure().apply { setPath(stroke, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * local, partial, true)
            drawPath(
                path = partial,
                color = Color.White,
                style =
                    Stroke(
                        width = this.size.minDimension * StrokeShare,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }
}

/** One [Path] per continuous stroke: PathMeasure walks a single contour, and a cross has two. */
@Suppress("MagicNumber") // glyph coordinates are fractions of the mark's box: a drawing table, not logic.
private fun glyphStrokes(
    kind: TerminalMarkKind,
    d: Float,
): List<Path> {
    fun line(vararg pts: Pair<Float, Float>) =
        Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * d, y * d) else lineTo(x * d, y * d) }
        }
    return when (kind) {
        TerminalMarkKind.Success -> listOf(line(0.29f to 0.52f, 0.44f to 0.67f, 0.72f to 0.36f))
        TerminalMarkKind.Failure ->
            listOf(line(0.33f to 0.33f, 0.67f to 0.67f), line(0.67f to 0.33f, 0.33f to 0.67f))
        TerminalMarkKind.Pending -> listOf(line(0.5f to 0.28f, 0.5f to 0.5f, 0.66f to 0.6f))
    }
}

private const val DiscShare = 0.35f
private const val StrokeShare = 0.07f
