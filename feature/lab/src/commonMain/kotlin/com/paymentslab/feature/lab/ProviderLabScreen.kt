package com.paymentslab.feature.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.FailureShake
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.PaymentFlowDiagram
import com.paymentslab.core.designsystem.PrimaryButton
import com.paymentslab.core.designsystem.SectionHeader
import com.paymentslab.core.designsystem.ShieldPulse
import com.paymentslab.core.designsystem.SuccessBurst
import com.paymentslab.feature.lab.explain.ErrorExplainer
import com.paymentslab.feature.lab.explain.ExplainerPanel
import com.siddharth.kmp.designsystem.StepTimeline
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentStatus
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

/**
 * Stateful entry point for a provider's live lab. [paymentHost] is the platform payment host the
 * app supplies (the real `AndroidPaymentHost`) — the screen never constructs it, keeping Activity
 * references out of feature code.
 */
@Composable
fun ProviderLabRoot(
    paymentHost: PaymentHost,
    gatewayId: GatewayId,
    providerName: String,
    priceLabel: String,
    catalogItemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderLabViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Not every composition root wires an AI seam (the web preview and iOS don't) — resolved from
    // the global Koin context rather than koinViewModel/koinInject so a missing binding degrades to
    // null (deterministic-only explanations) instead of crashing. See labAiModule's own KDoc for why
    // this binding is Android-only today.
    val explainer = remember { GlobalContext.getOrNull()?.getOrNull<ErrorExplainer>() }
    ProviderLabScreen(
        state = state,
        providerName = providerName,
        priceLabel = priceLabel,
        explainer = explainer,
        onPay = { viewModel.start(paymentHost, gatewayId, catalogItemId) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless live-payment lab: a big [StepTimeline] of the orchestrator's steps and a
 * [PrimaryButton] that kicks off (or replays) the sandbox payment.
 */
@Composable
fun ProviderLabScreen(
    state: ProviderLabUiState,
    providerName: String,
    priceLabel: String,
    onPay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    explainer: ErrorExplainer? = null,
) {
    LabScaffold(title = providerName, onBack = onBack) { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            ) {
                ShieldPulse()
                Text(
                    text = "Protected — screenshots and screen recording are blocked here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Pinned at top per the plan: shows this run's specific hops (SDK vs WebView vs intent
            // vs poll) and the trust boundary — a client result stays "unverified" colour until the
            // backend actually confirms it.
            state.currentHop?.let { hop ->
                PaymentFlowDiagram(
                    activeHop = hop,
                    verified = state.verified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(text = "Live payment timeline")

            if (state.steps.isEmpty()) {
                Text(
                    text =
                        "Tap the button below to run a sandbox payment and watch every hop — " +
                            "order creation, launch, client result, verification and the server-authoritative outcome.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StepTimeline(
                    steps = state.steps,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val buttonText =
                if (state.hasRun && !state.isRunning) {
                    "Run again"
                } else {
                    "Pay $priceLabel (sandbox)"
                }
            PrimaryButton(
                text = buttonText,
                onClick = onPay,
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            state.finalStatus?.let { status -> TerminalStatusBadge(status) }

            state.gatewayFailure?.let { failure ->
                ExplainerPanel(
                    failure = failure,
                    explainer = explainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The settled-run readout: a success/failure sting plus the server's own terminal status name. */
@Composable
private fun TerminalStatusBadge(
    status: PaymentStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        if (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUNDED) {
            SuccessBurst()
        } else {
            FailureShake()
        }
        Text(
            text = "Final server status: ${status.name}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
