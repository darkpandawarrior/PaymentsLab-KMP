package com.paymentslab.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pins the WCAG 4.5:1 floor for every status label on its own tint, in both schemes. */
class StatusContrastTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun check(
        surface: Color,
        onSurface: Color,
    ) {
        StatusTone.entries.forEach { tone ->
            val tint = tone.color.copy(alpha = 0.14f).compositeOver(surface)
            val ratio = contrast(tone.color.asLabelOn(onSurface), tint)
            assertTrue(ratio >= 4.5f, "$tone label is $ratio:1 on its tint")
        }
    }

    @Test
    fun lightLabelsClear45() = check(Color(0xFFFBFAFF), Color(0xFF1B1B23))

    @Test
    fun darkLabelsClear45() = check(Color(0xFF121218), Color(0xFFE4E1E9))
}
