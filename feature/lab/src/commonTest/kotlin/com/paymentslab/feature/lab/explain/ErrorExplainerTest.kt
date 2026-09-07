package com.paymentslab.feature.lab.explain

import app.cash.turbine.test
import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.RedactedPayload
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Streams a fixed sequence of tokens, or a single [AiChunk.Failed], recording every prompt it saw. */
private class ScriptedProvider(
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

private fun declineFailure(phase: PaymentPhase = PaymentPhase.TERMINAL) =
    GatewayFailure(
        gatewayId = GatewayId("razorpay"),
        code = FailureCode.GATEWAY_DECLINED,
        payload = RedactedPayload.of("razorpay_result", "code" to "2", "description" to "Card declined"),
        phase = phase,
    )

class ErrorExplainerTest {
    // ── the deterministic floor: always available, phase changes the reading ───────────────────────
    @Test
    fun deterministic_readsMoreTentative_midFlowThanOnceTerminal() {
        val explainer = ErrorExplainer(providers = emptyList())

        val midFlow = explainer.deterministic(declineFailure(PaymentPhase.VERIFYING))
        val terminal = explainer.deterministic(declineFailure(PaymentPhase.TERMINAL))

        assertNotEquals(midFlow, terminal, "the same FailureCode must read differently by phase")
        assertTrue(terminal.contains("bank declined", ignoreCase = true))
        assertTrue(
            midFlow.contains("still confirming", ignoreCase = true) || midFlow.contains("not final", ignoreCase = true),
        )
    }

    @Test
    fun deterministic_userCancelled_neverBlamesTheGateway() {
        val explainer = ErrorExplainer(providers = emptyList())
        val cancelled = declineFailure().copy(code = FailureCode.USER_CANCELLED)

        val text = explainer.deterministic(cancelled)

        assertTrue(text.contains("cancelled", ignoreCase = true))
    }

    // ── streamRicher: no providers wired at all (web/iOS today) → silent, deterministic stands alone ──
    @Test
    fun streamRicher_emitsNothing_whenNoProviderIsWired() =
        runTest {
            val explainer = ErrorExplainer(providers = emptyList())

            explainer.streamRicher(declineFailure()).test {
                awaitComplete()
            }
        }

    // ── streamRicher: tokens accumulate in order as the model streams ───────────────────────────────
    @Test
    fun streamRicher_accumulatesTokensInOrder() =
        runTest {
            val provider = ScriptedProvider(tokens = listOf("The ", "bank ", "flagged fraud."))
            val explainer = ErrorExplainer(providers = listOf(provider))

            explainer.streamRicher(declineFailure()).test {
                assertEquals(ModelExplanation.Partial("The "), awaitItem())
                assertEquals(ModelExplanation.Partial("The bank "), awaitItem())
                assertEquals(ModelExplanation.Partial("The bank flagged fraud."), awaitItem())
                awaitComplete()
            }
        }

    // ── the raw gateway payload reaches the model as a USER message — PromptGuard wraps it upstream
    // (buildProviderChain's GuardedAiProvider), this only proves the payload's own text is actually
    // in the prompt sent, not silently dropped ──────────────────────────────────────────────────────
    @Test
    fun streamRicher_sendsTheRawPayloadDescription_asAUserMessage() =
        runTest {
            val provider = ScriptedProvider()
            val explainer = ErrorExplainer(providers = listOf(provider))

            explainer.streamRicher(declineFailure()).test {
                awaitItem()
                awaitComplete()
            }

            val sent = provider.lastMessages.orEmpty()
            assertEquals(1, sent.size)
            assertEquals(AiMessage.Role.USER, sent.single().role)
            assertTrue(sent.single().content.contains("Card declined"))
        }

    @Test
    fun streamRicher_skipsAnUnavailableProvider_forTheFallback() =
        runTest {
            val unavailable = ScriptedProvider(available = false, tokens = listOf("unreachable"))
            val fallback = ScriptedProvider(available = true, tokens = listOf("fallback text"))
            val explainer = ErrorExplainer(providers = listOf(unavailable, fallback))

            explainer.streamRicher(declineFailure()).test {
                assertEquals(ModelExplanation.Partial("fallback text"), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun streamRicher_reportsFailed_whenTheModelCallItselfFails() =
        runTest {
            val provider = ScriptedProvider(tokens = null, failure = AiFailure.RateLimited)
            val explainer = ErrorExplainer(providers = listOf(provider))

            explainer.streamRicher(declineFailure()).test {
                assertEquals(ModelExplanation.Failed(AiFailure.RateLimited), awaitItem())
                awaitComplete()
            }
        }
}
