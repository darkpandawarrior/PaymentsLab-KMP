package com.paymentslab.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** UI-level gateway availability, mapped to a coloured pill by [GatewayStatusBadge]. */
enum class GatewayStatusUi {
    SANDBOX_READY,
    MOCK_MODE,
    KYC_GATED,
    COMING_SOON,
}

private data class BadgeSpec(
    val label: String,
    val tone: StatusTone,
)

private fun GatewayStatusUi.spec(): BadgeSpec =
    when (this) {
        GatewayStatusUi.SANDBOX_READY -> BadgeSpec("Sandbox ready", StatusTone.Success)
        GatewayStatusUi.MOCK_MODE -> BadgeSpec("Mock mode", StatusTone.Info)
        GatewayStatusUi.KYC_GATED -> BadgeSpec("KYC gated", StatusTone.Warning)
        GatewayStatusUi.COMING_SOON -> BadgeSpec("Coming soon", StatusTone.Neutral)
    }

/**
 * A small tinted pill communicating a gateway's availability: a status dot plus a label on a
 * low-alpha fill of the same tone. Rendered by [StatusPill].
 */
@Composable
fun GatewayStatusBadge(
    status: GatewayStatusUi,
    modifier: Modifier = Modifier,
) {
    val spec = status.spec()
    StatusPill(
        label = spec.label,
        tone = spec.tone,
        modifier = modifier.shimmer(active = status == GatewayStatusUi.MOCK_MODE),
    )
}
