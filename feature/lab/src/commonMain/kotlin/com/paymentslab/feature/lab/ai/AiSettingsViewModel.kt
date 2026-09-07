package com.paymentslab.feature.lab.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddharth.kmp.ai.ModelManager
import com.siddharth.kmp.ai.ModelManifestEntry
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.designsystem.ai.AiSettingsState
import com.siddharth.kmp.designsystem.ai.AiSettingsUiState
import com.siddharth.kmp.llmchat.ProviderId
import com.siddharth.kmp.llmchat.SecureKeyStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin ViewModel wrapper around the toolkit's [AiSettingsState] — same state holder every AI
 * settings screen in the family drives, given a [viewModelScope] and this app's own
 * [ModelManager]/[OnDeviceLlm]/[SecureKeyStore] bindings (see `AiModule.kt`). No logic of its own:
 * [AiSettingsState] already owns consent, model download and provider-key state.
 */
class AiSettingsViewModel(
    modelManager: ModelManager,
    manifest: List<ModelManifestEntry>,
    onDeviceLlm: OnDeviceLlm,
    keyStore: SecureKeyStore,
) : ViewModel() {
    private val delegate =
        AiSettingsState(
            modelManager = modelManager,
            manifest = manifest,
            onDeviceLlm = onDeviceLlm,
            getKey = keyStore::getKey,
            setKey = keyStore::setKey,
            scope = viewModelScope,
        )

    val uiState: StateFlow<AiSettingsUiState> = delegate.uiState

    fun onConsentChange(consent: Boolean) = delegate.setAiConsent(consent)

    fun onSelectProvider(providerId: ProviderId) = delegate.selectProvider(providerId)

    fun onProviderKeyChange(
        providerId: ProviderId,
        apiKey: String,
    ) = delegate.setProviderKey(providerId, apiKey)

    fun onClearProviderKey(providerId: ProviderId) = delegate.clearProviderKey(providerId)

    fun onTestProviderKey(providerId: ProviderId) = delegate.testKey(providerId)

    fun onStartDownload(
        modelId: String,
        licenseAcknowledged: Boolean,
    ) = delegate.startDownload(modelId, licenseAcknowledged)

    fun onPauseDownload(modelId: String) = delegate.pauseDownload(modelId)

    fun onDeleteModel(modelId: String) = delegate.deleteModel(modelId)
}
