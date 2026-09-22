package com.paymentslab.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * How to render a gateway's identity: either a [Logo] (a real bundled vector mark) or a
 * [Monogram] (a colored lettermark) when no real asset is registered for that gateway.
 */
sealed interface GatewayBrandAsset {
    data class Logo(
        val imageVector: ImageVector,
    ) : GatewayBrandAsset

    data class Monogram(
        val letter: Char,
        val color: Color,
    ) : GatewayBrandAsset
}

/**
 * Maps a gateway ID (matching [com.siddharth.kmp.paymentsapi.GatewayId.value] — this module
 * intentionally does not depend on `core:payments-api`, so it takes a plain [String]) to a
 * [GatewayBrandAsset].
 *
 * [curatedLogos] holds real, license-checked vector marks for the handful of gateways with a
 * clean, fetchable brand asset (populated in a later task). Every other gateway — the ~50+ niche
 * regional processors, and any gateway added to the catalog later — deterministically hashes to
 * one of [monogramPalette], so nothing ever falls through to a generic default gray circle.
 */
object GatewayBranding {
    /** Fixed, AA-contrast-checked palette (white lettermark text on each) for the hash fallback. */
    private val monogramPalette =
        listOf(
            Color(0xFF6D28D9), // violet
            Color(0xFF0EA5E9), // sky
            Color(0xFFDC2626), // red
            Color(0xFF059669), // emerald
            Color(0xFFD97706), // amber
            Color(0xFFDB2777), // pink
            Color(0xFF0891B2), // cyan
            Color(0xFF65A30D), // lime
            Color(0xFFE11D48), // rose
            Color(0xFF7C3AED), // purple
        )

    /** Populated by `registerCuratedLogo` calls in a later task's `GatewayBrandingLogos.kt` file. */
    internal val curatedLogos = mutableMapOf<String, ImageVector>()

    fun forId(
        id: String,
        displayName: String = id,
    ): GatewayBrandAsset {
        curatedLogos[id]?.let { return GatewayBrandAsset.Logo(it) }
        return GatewayBrandAsset.Monogram(
            letter = displayName.firstOrNull()?.uppercaseChar() ?: '?',
            color = monogramPalette[id.stableHashIndex(monogramPalette.size)],
        )
    }
}

/**
 * A hash index that's stable across Android/iOS, not just "whatever the JVM/Kotlin-Native
 * `hashCode()` happens to return today" — Kotlin's stdlib specifies the same polynomial hash
 * algorithm for `String.hashCode()` on every target, but this local implementation makes the
 * dependency explicit and immune to a future stdlib change, since badge colors being stable across
 * app updates (not just across platforms) is the actual requirement.
 */
private fun String.stableHashIndex(bucketCount: Int): Int {
    var hash = 0
    for (char in this) {
        hash = (hash * HashMultiplier + char.code) and PositiveIntMask
    }
    return hash % bucketCount
}

/** The multiplier `String.hashCode()` is specified with; pinned here so the index never moves. */
private const val HashMultiplier = 31

/** Clears the sign bit, so the running hash stays non-negative and `%` yields a valid bucket. */
private const val PositiveIntMask = 0x7FFFFFFF
