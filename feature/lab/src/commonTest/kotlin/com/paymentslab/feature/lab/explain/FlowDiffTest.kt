package com.paymentslab.feature.lab.explain

import app.cash.turbine.test
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentSnapshot
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import com.siddharth.kmp.paymentsapi.PendingReason
import com.siddharth.kmp.paymentsapi.RedactedPayload
import com.siddharth.kmp.paymentsapi.SplitLeg
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Same scripted double as ErrorExplainerTest — records the prompt it saw, replays fixed tokens. */
private class DiffScriptedProvider(
    private val available: Boolean = true,
    private val tokens: List<String>? = listOf("more detail"),
    private val failure: AiFailure? = null,
) : AiProvider {
    override val id: String = "scripted"
    override val displayName: String = id
    var lastMessages: List<AiMessage>? = null
        private set

    override suspend fun isAvailable(): Boolean = available

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success(tokens.orEmpty().joinToString(""))

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> =
        flow {
            lastMessages = messages
            failure?.let {
                emit(AiChunk.Failed(it))
                return@flow
            }
            tokens.orEmpty().forEach { emit(AiChunk.Token(it)) }
        }
}

private fun order() = PaymentStep.OrderCreated("order-1", Money(10000, "INR"), RedactedPayload.EMPTY)

private fun launching(gatewayId: GatewayId) = PaymentStep.Launching(gatewayId)

private fun clientSuccess() =
    PaymentStep.ClientResult(PaymentResult.Success("pay-1", emptyMap(), RedactedPayload.EMPTY), RedactedPayload.EMPTY)

private fun clientAwaitingWebhook() =
    PaymentStep.ClientResult(PaymentResult.Pending(PendingReason.AWAITING_WEBHOOK), payload = RedactedPayload.EMPTY)

private fun settled(status: PaymentStatus) =
    PaymentStep.Settled(status, PaymentSnapshot("order-1", "pay-1", status), RedactedPayload.EMPTY)

/** A single, ordinary single-leg run: order → launch → client success → settled SUCCESS. */
private fun happyPathTrace(gatewayId: GatewayId): List<PaymentStep> =
    listOf(order(), launching(gatewayId), clientSuccess(), settled(PaymentStatus.SUCCESS))

/** Same shape as [happyPathTrace] but settles FAILED — used by every streamNarrative test below,
 *  which only cares that a diff with one divergence exists, not which kind. */
private fun failedTrace(gatewayId: GatewayId): List<PaymentStep> =
    listOf(order(), launching(gatewayId), clientSuccess(), settled(PaymentStatus.FAILED))

class FlowDiffTest {
    // ── deterministic diff: identical shapes diverge on nothing ─────────────────────────────────
    @Test
    fun compare_identicalShapedTraces_reportsNoDivergence() {
        val baseline = GatewayId("razorpay")
        val compared = GatewayId("stripe")

        val diff =
            FlowDiff.compare(
                baseline = FlowTrace.from(baseline, happyPathTrace(baseline)),
                compared = FlowTrace.from(compared, happyPathTrace(compared)),
            )

        assertTrue(diff.divergences.isEmpty())
        assertTrue(!diff.hasDivergence)
    }

    // ── different terminal states ────────────────────────────────────────────────────────────────
    @Test
    fun compare_differentTerminalStatus_reportsTerminalStatusDiffers() {
        val baseline = GatewayId("razorpay")
        val compared = GatewayId("stripe")
        val comparedTrace = failedTrace(compared)

        val diff =
            FlowDiff.compare(
                baseline = FlowTrace.from(baseline, happyPathTrace(baseline)),
                compared = FlowTrace.from(compared, comparedTrace),
            )

        val divergence = diff.divergences.filterIsInstance<FlowDivergence.TerminalStatusDiffers>().single()
        assertEquals(PaymentStatus.SUCCESS, divergence.baselineStatus)
        assertEquals(PaymentStatus.FAILED, divergence.comparedStatus)
    }

    // ── extra leg (split payment): the compared gateway settles TWO legs, the baseline settles one ──
    @Test
    fun compare_comparedHasAnExtraLeg_reportsExtraLegDivergence() {
        val baseline = GatewayId("razorpay")
        val compared = GatewayId("wallet-plus-gateway")
        val splitTrace =
            listOf(
                order(),
                launching(compared),
                clientSuccess(),
                PaymentStep.LegSettled(SplitLeg.WALLET, settled(PaymentStatus.SUCCESS)),
                PaymentStep.LegSettled(SplitLeg.GATEWAY, settled(PaymentStatus.SUCCESS)),
            )

        val diff =
            FlowDiff.compare(
                baseline = FlowTrace.from(baseline, happyPathTrace(baseline)),
                compared = FlowTrace.from(compared, splitTrace),
            )

        val divergence = diff.divergences.filterIsInstance<FlowDivergence.ExtraLeg>().single()
        assertEquals(compared, divergence.gatewayId)
        assertEquals(2, divergence.legCount)
        assertEquals(1, divergence.otherLegCount)
    }

    // ── webhook-only capture: one gateway's client SDK never confirms, it settles off a webhook ──
    @Test
    fun compare_oneGatewayAwaitsWebhookOnly_reportsWebhookOnlyCapture() {
        val baseline = GatewayId("razorpay")
        val compared = GatewayId("hosted-webview")
        val webhookTrace = listOf(order(), launching(compared), clientAwaitingWebhook(), settled(PaymentStatus.SUCCESS))

        val diff =
            FlowDiff.compare(
                baseline = FlowTrace.from(baseline, happyPathTrace(baseline)),
                compared = FlowTrace.from(compared, webhookTrace),
            )

        val divergence = diff.divergences.filterIsInstance<FlowDivergence.WebhookOnlyCapture>().single()
        assertEquals(compared, divergence.gatewayId)
    }

    // ── deterministic summary reads plainly, mentions both gateways ─────────────────────────────
    @Test
    fun deterministicSummary_mentionsBothGatewayIdsAndEveryDivergence() {
        val baseline = GatewayId("razorpay")
        val compared = GatewayId("stripe")
        val comparedTrace = failedTrace(compared)

        val diff =
            FlowDiff.compare(
                baseline = FlowTrace.from(baseline, happyPathTrace(baseline)),
                compared = FlowTrace.from(compared, comparedTrace),
            )

        val summary = diff.deterministicSummary()
        assertTrue(summary.contains("razorpay"))
        assertTrue(summary.contains("stripe"))
        assertTrue(summary.contains("FAILED", ignoreCase = true))
    }

    // ── streamed narrative on top: same shape as ErrorExplainer.streamRicher ────────────────────
    @Test
    fun streamNarrative_emitsNothing_whenNoProviderIsWired() =
        runTest {
            val baseline = GatewayId("razorpay")
            val compared = GatewayId("stripe")
            val diff =
                FlowDiff.compare(
                    FlowTrace.from(baseline, happyPathTrace(baseline)),
                    FlowTrace.from(compared, failedTrace(compared)),
                )
            val explainer = FlowDiffExplainer(providers = emptyList())

            explainer.streamNarrative(diff).test {
                awaitComplete()
            }
        }

    @Test
    fun streamNarrative_accumulatesTokensInOrder() =
        runTest {
            val baseline = GatewayId("razorpay")
            val compared = GatewayId("stripe")
            val diff =
                FlowDiff.compare(
                    FlowTrace.from(baseline, happyPathTrace(baseline)),
                    FlowTrace.from(compared, failedTrace(compared)),
                )
            val provider = DiffScriptedProvider(tokens = listOf("Stripe ", "declined ", "server-side."))
            val explainer = FlowDiffExplainer(providers = listOf(provider))

            explainer.streamNarrative(diff).test {
                assertEquals(ModelExplanation.Partial("Stripe "), awaitItem())
                assertEquals(ModelExplanation.Partial("Stripe declined "), awaitItem())
                assertEquals(ModelExplanation.Partial("Stripe declined server-side."), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun streamNarrative_sendsTheDeterministicSummary_asAUserMessage() =
        runTest {
            val baseline = GatewayId("razorpay")
            val compared = GatewayId("stripe")
            val diff =
                FlowDiff.compare(
                    FlowTrace.from(baseline, happyPathTrace(baseline)),
                    FlowTrace.from(compared, failedTrace(compared)),
                )
            val provider = DiffScriptedProvider()
            val explainer = FlowDiffExplainer(providers = listOf(provider))

            explainer.streamNarrative(diff).test {
                awaitItem()
                awaitComplete()
            }

            val sent = provider.lastMessages.orEmpty()
            assertEquals(1, sent.size)
            assertEquals(AiMessage.Role.USER, sent.single().role)
            assertTrue(sent.single().content.contains(diff.deterministicSummary()))
        }

    @Test
    fun streamNarrative_reportsFailed_whenTheModelCallItselfFails() =
        runTest {
            val baseline = GatewayId("razorpay")
            val compared = GatewayId("stripe")
            val diff =
                FlowDiff.compare(
                    FlowTrace.from(baseline, happyPathTrace(baseline)),
                    FlowTrace.from(compared, failedTrace(compared)),
                )
            val provider = DiffScriptedProvider(tokens = null, failure = AiFailure.RateLimited)
            val explainer = FlowDiffExplainer(providers = listOf(provider))

            explainer.streamNarrative(diff).test {
                assertEquals(ModelExplanation.Failed(AiFailure.RateLimited), awaitItem())
                awaitComplete()
            }
        }
}
