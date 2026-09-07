package com.paymentslab.feature.lab.explain

import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.result.AiFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The structural facts [FlowDiff.compare] needs about ONE gateway's completed run, extracted once
 * from its recorded [PaymentStep] trace (the same list [com.paymentslab.feature.lab.ProviderLabViewModel]
 * already accumulates while driving a live payment) so the comparison itself stays a plain diff of
 * two small value objects rather than re-walking both step lists side by side.
 */
data class FlowTrace(
    val gatewayId: GatewayId,
    val terminalStatus: PaymentStatus?,
    /** 1 for an ordinary run; 2 when the trace is a split payment (wallet leg + gateway leg) — an
     *  extra hop the user actually sees, the closest this app's model has to "an extra redirect". */
    val legCount: Int,
    /** True when the last client-reported result before settling was [PendingReason.AWAITING_WEBHOOK]
     *  — this gateway's SDK never confirmed the payment itself; settlement depended on the backend
     *  hearing from a webhook (or polling) instead. */
    val awaitingWebhookOnly: Boolean,
) {
    companion object {
        fun from(
            gatewayId: GatewayId,
            steps: List<PaymentStep>,
        ): FlowTrace {
            val legSettledCount = steps.count { it is PaymentStep.LegSettled }
            val lastClientResult = steps.filterIsInstance<PaymentStep.ClientResult>().lastOrNull()?.result
            return FlowTrace(
                gatewayId = gatewayId,
                terminalStatus = steps.terminalStatus(),
                legCount = if (legSettledCount > 0) legSettledCount else 1,
                awaitingWebhookOnly =
                    lastClientResult is PaymentResult.Pending &&
                        lastClientResult.reason == PendingReason.AWAITING_WEBHOOK,
            )
        }

        private fun List<PaymentStep>.terminalStatus(): PaymentStatus? =
            filterIsInstance<PaymentStep.Settled>().lastOrNull()?.status
                ?: filterIsInstance<PaymentStep.LegSettled>().lastOrNull()?.settled?.status
    }
}

/** One structural difference between a [FlowDiff.baseline] and [FlowDiff.compared] trace, in a fixed,
 *  named vocabulary — deliberately small: these are the divergences the Lab's recorded traces can
 *  actually represent (see [FlowTrace]), not a general-purpose step-by-step diff. */
sealed interface FlowDivergence {
    data class TerminalStatusDiffers(
        val baselineStatus: PaymentStatus?,
        val comparedStatus: PaymentStatus?,
    ) : FlowDivergence

    /** [gatewayId] settles in [legCount] legs, more than the other trace's [otherLegCount]. */
    data class ExtraLeg(
        val gatewayId: GatewayId,
        val legCount: Int,
        val otherLegCount: Int,
    ) : FlowDivergence

    data class WebhookOnlyCapture(
        val gatewayId: GatewayId,
    ) : FlowDivergence
}

/** The deterministic comparison of two gateways' recorded runs — always available, no network,
 *  no key; [FlowDiffExplainer.streamNarrative] adds a model-written paragraph on top of it. */
data class FlowDiff(
    val baseline: FlowTrace,
    val compared: FlowTrace,
    val divergences: List<FlowDivergence>,
) {
    val hasDivergence: Boolean get() = divergences.isNotEmpty()

    /** A plain-English line per divergence, always present even with zero providers wired. */
    fun deterministicSummary(): String {
        if (!hasDivergence) {
            return "${baseline.gatewayId.value} and ${compared.gatewayId.value} followed the same shape of flow."
        }
        return divergences.joinToString("\n") { it.describe(baseline.gatewayId, compared.gatewayId) }
    }

    companion object {
        fun compare(
            baseline: FlowTrace,
            compared: FlowTrace,
        ): FlowDiff {
            val divergences =
                buildList {
                    if (baseline.terminalStatus != compared.terminalStatus) {
                        add(FlowDivergence.TerminalStatusDiffers(baseline.terminalStatus, compared.terminalStatus))
                    }
                    if (baseline.legCount != compared.legCount) {
                        // Whichever side actually has more legs is the one the divergence is "about" —
                        // reads correctly regardless of which trace was passed as baseline vs compared.
                        val moreLegs = if (compared.legCount > baseline.legCount) compared else baseline
                        val fewerLegs = if (moreLegs === compared) baseline else compared
                        add(FlowDivergence.ExtraLeg(moreLegs.gatewayId, moreLegs.legCount, fewerLegs.legCount))
                    }
                    if (baseline.awaitingWebhookOnly != compared.awaitingWebhookOnly) {
                        val webhookGateway =
                            if (compared.awaitingWebhookOnly) compared.gatewayId else baseline.gatewayId
                        add(FlowDivergence.WebhookOnlyCapture(webhookGateway))
                    }
                }
            return FlowDiff(baseline, compared, divergences)
        }
    }
}

private fun FlowDivergence.describe(
    baselineId: GatewayId,
    comparedId: GatewayId,
): String =
    when (this) {
        is FlowDivergence.TerminalStatusDiffers ->
            "${baselineId.value} settled $baselineStatus while ${comparedId.value} settled $comparedStatus."
        is FlowDivergence.ExtraLeg ->
            "${gatewayId.value} pays in $legCount legs (vs $otherLegCount) — an extra hop the other gateway " +
                "doesn't need."
        is FlowDivergence.WebhookOnlyCapture ->
            "${gatewayId.value}'s SDK never confirms the payment itself; it settles only once the backend " +
                "hears from a webhook."
    }

/**
 * Streams the model's richer take on top of [FlowDiff.deterministicSummary]. Same two-tier shape as
 * [ErrorExplainer]: the deterministic line always stands alone, this adds specific narrative detail
 * on request. See [ErrorExplainer]'s KDoc for the PromptGuard note — the same applies here, the
 * deterministic summary is Claude-authored text about the app's OWN recorded steps, not third-party
 * text, but the raw gateway id strings inside it still flow through whichever provider chain the
 * caller wired, which already wraps USER-role content in PromptGuard upstream.
 */
class FlowDiffExplainer(
    private val providers: List<AiProvider>,
) {
    fun streamNarrative(diff: FlowDiff): Flow<ModelExplanation> =
        flow {
            if (providers.isEmpty()) return@flow
            val provider = providers.firstOrNull { it.isAvailable() } ?: providers.last()

            val accumulated = StringBuilder()
            var failureReason: AiFailure? = null
            provider
                .completeStream(listOf(AiMessage(AiMessage.Role.USER, buildPrompt(diff))))
                .collect { chunk ->
                    when (chunk) {
                        is AiChunk.Token -> {
                            accumulated.append(chunk.text)
                            emit(ModelExplanation.Partial(accumulated.toString()))
                        }
                        is AiChunk.Failed -> failureReason = chunk.reason
                    }
                }
            failureReason?.let { emit(ModelExplanation.Failed(it)) }
        }

    private fun buildPrompt(diff: FlowDiff): String =
        buildString {
            appendLine(
                "Comparing two payment gateways' recorded flows: '${diff.baseline.gatewayId.value}' " +
                    "(baseline) vs '${diff.compared.gatewayId.value}' (compared).",
            )
            appendLine("A deterministic diff is already shown to the user:")
            appendLine(diff.deterministicSummary())
            appendLine()
            append(
                "In 2-3 short sentences, explain to an integrator WHY these gateways' flows likely " +
                    "differ this way (e.g. regulatory redirect requirements, async settlement, webhook-first " +
                    "capture) — beyond what the deterministic summary already said. Do not repeat it verbatim.",
            )
        }
}
