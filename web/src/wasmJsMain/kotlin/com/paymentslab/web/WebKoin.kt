package com.paymentslab.web

import com.paymentslab.core.gatewaycatalog.allHostedGatewayConfigs
import com.paymentslab.core.gatewaycatalog.allStubGatewayConfigs
import com.paymentslab.core.gatewaycatalog.stubGatewayModule
import com.paymentslab.core.orchestration.OrchestratorFlowRunner
import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.paymentslab.core.orchestration.di.orchestrationModule
import com.paymentslab.feature.checkoutdemo.CheckoutViewModel
import com.paymentslab.feature.lab.LabHomeViewModel
import com.paymentslab.feature.lab.ProviderLabViewModel
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentBackend
import com.siddharth.kmp.paymentsapi.PendingPaymentJournal
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome
import com.siddharth.kmp.provider.hostedwebview.di.hostedWebViewModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The web preview's composition root — the browser counterpart to `ios/shared/KoinInit.kt`. Only
 * the wasm-capable slice is wired: the shared `:core:gateway-catalog` hosted-webview + stub lists
 * (the same 44 + 3 rows Android and iOS read) over in-memory backend/journal fakes. The
 * mobile-money and wallet archetypes are absent because their provider modules publish no wasmJs
 * target, not because the browser could not show them. The feature modules' own Koin modules (`labModule`/`checkoutDemoModule`) are redeclared
 * here rather than imported: the checkout ViewModel needs the widened MOCK_MODE gateway filter,
 * and the lab module would otherwise double-register the same [PaymentFlowRunner] binding.
 */
fun initWebKoin(): Koin =
    startKoin {
        modules(
            module {
                single<PaymentBackend> { InMemoryPaymentBackend() }
                single<PendingPaymentJournal> { InMemoryPendingPaymentJournal() }
            },
            orchestrationModule,
            hostedWebViewModule(allHostedGatewayConfigs + webOnlyHostedGatewayConfigs),
            // Catalog-only rows. `StubGateway` lives in payments-api, which publishes wasmJs, so
            // the browser can show the same docs-only entries the Android catalog does.
            stubGatewayModule(allStubGatewayConfigs),
            module {
                single<PaymentFlowRunner> { OrchestratorFlowRunner(get()) }
                viewModel { LabHomeViewModel(get()) }
                viewModel { ProviderLabViewModel(get()) }
                viewModel {
                    CheckoutViewModel(
                        flowRunner = get(),
                        registry = get(),
                        // Everything in the browser is simulated — offer the whole MOCK_MODE fleet.
                        offeredStatuses = setOf(GatewayStatus.SANDBOX_READY, GatewayStatus.MOCK_MODE),
                    )
                }
            },
        )
    }.koin

/**
 * Plays the role of `HostedCheckoutHost` on platforms with a real WebView: watches the shared
 * [HostedCheckoutRelay] and resolves every launched checkout as a successful mock return after a
 * short "user is paying on the hosted page" pause. This is what lets the REAL
 * `HostedWebViewGateway.pay()` suspend/resume path run end-to-end in the browser.
 */
fun startMockCheckoutAutoResolver(
    relay: HostedCheckoutRelay,
    scope: CoroutineScope,
) {
    scope.launch {
        relay.requests.collect { request ->
            if (request != null) {
                delay(MOCK_CHECKOUT_MS)
                relay.reportResult(
                    request.gatewayId,
                    HostedReturnOutcome.Success(paymentId = "mock_pay_${request.gatewayId.value}"),
                )
            }
        }
    }
}

private const val MOCK_CHECKOUT_MS = 1_400L
