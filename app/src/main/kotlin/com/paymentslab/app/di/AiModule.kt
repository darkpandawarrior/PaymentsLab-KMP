package com.paymentslab.app.di

import com.siddharth.kmp.ai.ModelManifestEntry
import com.siddharth.kmp.ai.onDeviceLlmModule
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.AiProviderConfig
import com.siddharth.kmp.llmchat.SecureKeyStore
import com.siddharth.kmp.llmchat.buildProviderChain
import com.siddharth.kmp.llmchat.loadAiProviderConfig
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Wires the toolkit's AI seam into PaymentsLab-KMP: the on-device LLM tier (ML Kit / MediaPipe on
 * Android, per [onDeviceLlmModule]'s own platform gate — the same [com.siddharth.kmp.result.AiCapabilities]
 * honesty every other family app reports through) plus the cloud BYOK provider chain, both sharing
 * the [AiResult]/[AiFailure] vocabulary from `:result`. Consumed today by the lab's AI settings
 * screen ([com.paymentslab.feature.lab.ai.AiSettingsViewModel]); the [List] binding is here so a
 * future feature can use the chain without re-wiring it.
 */
val aiModule =
    module {
        includes(onDeviceLlmModule())

        single { SecureKeyStore(androidContext()) }

        single<AiProviderConfig> {
            val store = get<SecureKeyStore>()
            loadAiProviderConfig(getKey = store::getKey)
        }

        single<List<AiProvider>> { buildProviderChain(config = get(), fallback = NoBackendAiProvider) }

        // MediaPipe Gemma is the one downloadable on-device model this app manages; ML Kit Gemini
        // Nano (AICore-managed) and Foundation Models (OS-managed) need no manifest entry — see
        // ModelManager's own KDoc for why.
        single<List<ModelManifestEntry>> {
            listOf(
                ModelManifestEntry(
                    id = "gemma3-1b-it",
                    displayName = "Gemma 3 1B (on-device)",
                    approxSizeMb = GEMMA_3_1B_APPROX_SIZE_MB,
                    fileName = "gemma3-1b-it.task",
                    hfRepo = "litert-community/Gemma3-1B-IT",
                    hfFile = "gemma3-1b-it.task",
                    requiresLicenseAck = true,
                ),
            )
        }
    }

private const val GEMMA_3_1B_APPROX_SIZE_MB = 554

/**
 * [buildProviderChain]'s required last resort when neither on-device nor any cloud key is
 * configured — every real call in that state should read as "nothing is set up", not a crash.
 *
 * // ponytail: a static NoKey; this app ships no app-specific offline heuristic tier (that would
 * // need its own model of "canned replies"), unlike a domain app with a rule-based fallback of
 * // its own.
 */
private object NoBackendAiProvider : AiProvider {
    override val id: String = "none"
    override val displayName: String = "Offline"

    override suspend fun isAvailable(): Boolean = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Failure(AiFailure.NoKey)
}
