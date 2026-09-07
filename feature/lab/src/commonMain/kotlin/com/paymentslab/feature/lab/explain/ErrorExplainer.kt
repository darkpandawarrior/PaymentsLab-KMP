package com.paymentslab.feature.lab.explain

import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.paymentslab.feature.lab.toPlainExplanation
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.RedactedPayload
import com.siddharth.kmp.result.AiFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Everything [ErrorExplainer] needs about one client-reported gateway decline: the normalized
 * [code], the redacted raw payload the provider actually returned, and the [PaymentPhase] (from
 * the orchestrator's `PaymentFsm`) it was seen in — `GatewayStatusMapping.toPlainExplanation` reads
 * the same [code] differently depending on [phase].
 */
data class GatewayFailure(
    val gatewayId: GatewayId,
    val code: FailureCode,
    val payload: RedactedPayload,
    val phase: PaymentPhase,
)

/** One update from [ErrorExplainer.streamRicher] as the model's elaboration streams in. */
sealed interface ModelExplanation {
    /** Accumulated text so far — a UI re-renders this in place as more tokens arrive. */
    data class Partial(
        val textSoFar: String,
    ) : ModelExplanation

    /** The model call itself failed (no key, no network, rate-limited, ...); nothing to add. */
    data class Failed(
        val reason: AiFailure,
    ) : ModelExplanation
}

/**
 * Explains a gateway failure in plain language, in two tiers:
 *  1. [deterministic] — the `GatewayStatusMapping` table. Always available: no suspension, no
 *     network, no key.
 *  2. [streamRicher] — asks whichever [providers] chain the app wired (first-available, cloud or
 *     on-device — see `buildProviderChain`) to add specific, model-written detail on top, streamed
 *     token by token.
 *
 * The raw gateway [GatewayFailure.payload] is third-party text (a provider's own error
 * description) and is never trusted as an instruction: every [providers] entry built via
 * `buildProviderChain` already wraps USER-role content in `PromptGuard` before it reaches any
 * backend, cloud or on-device, so that happens for free here — this class does not need to (and
 * must not) wrap it again.
 */
class ErrorExplainer(
    private val providers: List<AiProvider>,
) {
    /** The deterministic floor alone — pure, always available. */
    fun deterministic(failure: GatewayFailure): String = failure.code.toPlainExplanation(failure.phase)

    /**
     * Streams the model's richer take on top of [deterministic]. Emits nothing at all when no
     * provider chain was wired for this platform (e.g. the web preview, or iOS before its own AI
     * seam is bound) — [deterministic] already stands alone as the full explanation there.
     */
    fun streamRicher(
        failure: GatewayFailure,
        deterministic: String = deterministic(failure),
    ): Flow<ModelExplanation> =
        flow {
            if (providers.isEmpty()) return@flow
            // Providers are ordered on-device-first then cloud, with the app's fallback already
            // appended last by buildProviderChain — first available (or, failing that, that last
            // fallback) covers "no key configured" the same way every other AI seam in this app does.
            val provider = providers.firstOrNull { it.isAvailable() } ?: providers.last()

            val accumulated = StringBuilder()
            var failureReason: AiFailure? = null
            provider
                .completeStream(listOf(AiMessage(AiMessage.Role.USER, buildPrompt(failure, deterministic))))
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

    private fun buildPrompt(
        failure: GatewayFailure,
        deterministic: String,
    ): String {
        val rawEntries =
            failure.payload.entries
                .joinToString("\n") { (key, value) -> "$key: $value" }
                .ifEmpty { "(no raw fields)" }
        return buildString {
            appendLine(
                "A payment through gateway '${failure.gatewayId.value}' failed with normalized " +
                    "code ${failure.code} during phase ${failure.phase}.",
            )
            appendLine("A deterministic explanation is already shown to the user:")
            appendLine("\"$deterministic\"")
            appendLine()
            appendLine("Raw gateway payload (${failure.payload.label}):")
            append(rawEntries)
            appendLine()
            appendLine()
            append(
                "In 2-3 short sentences, add specific, actionable detail for a developer debugging " +
                    "this integration, beyond what the deterministic explanation already said. Do not " +
                    "repeat it verbatim.",
            )
        }
    }
}
