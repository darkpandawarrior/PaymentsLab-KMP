package com.paymentslab.feature.lab.ai

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.LabScaffold
import com.siddharth.kmp.designsystem.ai.AiSettingsSection
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: resolves [AiSettingsViewModel] and hands its state to [AiSettingsSection]. */
@Composable
fun LabSettingsRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LabScaffold(title = "AI settings", onBack = onBack) { padding ->
        AiSettingsSection(
            uiState = state,
            onConsentChange = viewModel::onConsentChange,
            onStartDownload = viewModel::onStartDownload,
            onPauseDownload = viewModel::onPauseDownload,
            onDeleteModel = viewModel::onDeleteModel,
            onSelectProvider = viewModel::onSelectProvider,
            onProviderKeyChange = viewModel::onProviderKeyChange,
            onClearProviderKey = viewModel::onClearProviderKey,
            onTestProviderKey = viewModel::onTestProviderKey,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.lg),
        )
    }
}
