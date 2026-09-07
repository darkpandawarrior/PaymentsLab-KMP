package com.paymentslab.feature.lab.di

import com.paymentslab.core.orchestration.OrchestratorFlowRunner
import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.paymentslab.feature.lab.LabHomeViewModel
import com.paymentslab.feature.lab.ProviderLabViewModel
import com.paymentslab.feature.lab.ai.AiSettingsViewModel
import com.paymentslab.feature.lab.explain.ErrorExplainer
import com.paymentslab.feature.lab.explain.FlowDiffExplainer
import com.paymentslab.feature.lab.explain.FlowDiffViewModel
import com.paymentslab.feature.lab.explain.RecordedFlowStore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the Integration Lab feature. Provides the catalog + provider-detail ViewModels
 * and binds the production [PaymentFlowRunner] over the orchestrator (resolved from
 * `orchestrationModule`). Every target that ships [labModule] must ship this.
 *
 * [RecordedFlowStore] and [FlowDiffViewModel] live here, not in [labAiModule]: comparing two
 * recorded flows needs no AI seam at all (see [FlowDiffScreen][com.paymentslab.feature.lab.explain.FlowDiffScreen]'s
 * deterministic-only floor), the same way [ErrorExplainer]'s deterministic tier does not.
 */
val labModule =
    module {
        single<PaymentFlowRunner> { OrchestratorFlowRunner(get()) }
        single { RecordedFlowStore() }
        viewModel { LabHomeViewModel(get()) }
        viewModel { ProviderLabViewModel(get(), get()) }
        viewModel { FlowDiffViewModel(get()) }
    }

/**
 * [AiSettingsViewModel]'s binding, kept separate from [labModule] because its dependencies
 * (ModelManager/OnDeviceLlm/ModelManifestEntry/SecureKeyStore) are only bound where an `:ai`-backed
 * DI graph exists — today that's `:app`'s `aiModule` (Android only). Include this alongside
 * `aiModule`; a composition root without it (iOS/web) must not include this module, or Koin will
 * fail to resolve these params at runtime the first time the ViewModel is requested.
 *
 * // ponytail: scoped-module split rather than adding iOS/web AI bindings with no UI consumer yet
 * (LabHomeScreen only wires the settings entry point on Android) — bind iOS's own onDeviceLlmModule
 * + SecureKeyStore actual and include this there once the settings screen is reachable on iOS too.
 *
 * [ErrorExplainer] and [FlowDiffExplainer] live here for the same reason: both need the app's
 * `List<AiProvider>` chain. `ProviderLabScreen`/`FlowDiffScreen` resolve them from the global Koin
 * context and tolerate them being absent (web, iOS) — see their own KDoc — so this binding's
 * absence there degrades gracefully rather than crashing.
 */
val labAiModule =
    module {
        viewModel { AiSettingsViewModel(get(), get(), get(), get()) }
        single { ErrorExplainer(get()) }
        single { FlowDiffExplainer(get()) }
    }
