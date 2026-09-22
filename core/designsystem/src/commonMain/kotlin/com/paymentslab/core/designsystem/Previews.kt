package com.paymentslab.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp

/**
 * Previews for the design system. Scoped here and nowhere else in the repo: this module is what
 * every feature renders through, so a contrast or layout bug shipped from these files reaches all
 * of them, and it is the only module where a preview has more than one reader.
 *
 * `androidx.compose.ui.tooling.preview.Preview` is THE multiplatform annotation since Compose
 * Multiplatform 1.10 — it ships in commonMain of `org.jetbrains.compose.ui:ui-tooling-preview`,
 * already a dependency here. `org.jetbrains.compose.ui.tooling.preview.Preview` is the deprecated
 * pre-1.10 one, and the `expect`/`actual` Preview shim older KMP posts recommend is obsolete.
 * Rendering is Android-side even for commonMain, which is why `ui-tooling` sits on
 * `androidRuntimeClasspath` in this module's build file — a preview tells you about the Android
 * rendering of shared UI, never about iOS fidelity.
 *
 * `@PreviewLightDark` is the only multipreview used, because [PaymentsLabTheme] really does swap
 * schemes and every colour below is a semantic token resolved against it. `@PreviewScreenSizes`
 * and `@PreviewFontScale` are six and seven renders each and stay off until a layout bug bites.
 */

@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    PaymentsLabTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/**
 * Every [GatewayStatusUi] in one render. Gateway coverage is what this repo is for, and the badge
 * is how a gateway's readiness is communicated — four tinted labels whose contrast against the
 * surface is the thing that silently fails when the scheme flips.
 */
@PreviewLightDark
@Composable
private fun GatewayStatusBadgesPreview() {
    PreviewShell {
        SectionHeader("Gateway readiness")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GatewayStatusUi.entries.forEach { status -> GatewayStatusBadge(status) }
        }
    }
}

/**
 * The repo's central lesson, pinned: a client-side `Success` is only a hint until the backend has
 * confirmed it. The diagram renders the packet in the "unverified" colour all the way to GATEWAY
 * and only switches once [PaymentFlowDiagram]'s `verified` is true. Both rows are here so the
 * distinction is visible side by side — if a refactor ever makes them render identically, this
 * preview is where that shows up.
 */
@PreviewLightDark
@Composable
private fun PaymentFlowVerifiedVsUnverifiedPreview() {
    PreviewShell {
        SectionHeader("Client says success")
        Text("unverified — the packet has only reached the gateway", style = MaterialTheme.typography.bodySmall)
        PaymentFlowDiagram(activeHop = FlowHop.GATEWAY, verified = false, modifier = Modifier.fillMaxWidth())
        SectionHeader("Backend confirmed")
        Text("verified — the webhook landed", style = MaterialTheme.typography.bodySmall)
        PaymentFlowDiagram(activeHop = FlowHop.WEBHOOK, verified = true, modifier = Modifier.fillMaxWidth())
    }
}

/** The chrome every lab screen is assembled from, at the states that differ. */
@PreviewLightDark
@Composable
private fun ChromeGalleryPreview() {
    PreviewShell {
        SectionHeader("Amount")
        AnimatedAmount(amountMinor = 124_500, currency = "INR")
        SectionHeader("Terminal feedback")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SuccessBurst()
            FailureShake()
        }
        SectionHeader("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "Pay now", onClick = {})
            PrimaryButton(text = "Pay now", onClick = {}, enabled = false)
        }
    }
}

/**
 * Narrow width, because the flow diagram and the badge row are the two things that have to survive
 * a phone in portrait with a long gateway name next to them.
 */
@Preview(widthDp = 240)
@Composable
private fun NarrowLayoutPreview() {
    PreviewShell {
        GatewayStatusBadge(GatewayStatusUi.KYC_GATED)
        PaymentFlowDiagram(activeHop = FlowHop.BACKEND, verified = false, modifier = Modifier.fillMaxWidth())
        AnimatedAmount(amountMinor = 9_999_900, currency = "INR")
    }
}
