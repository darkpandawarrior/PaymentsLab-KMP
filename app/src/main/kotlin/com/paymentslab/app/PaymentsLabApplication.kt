package com.paymentslab.app

import android.app.Application
import androidx.work.Configuration
import com.paymentslab.app.di.aiModule
import com.paymentslab.app.work.PaymentReconciliationWorker
import com.paymentslab.app.work.PaymentWorkerFactory
import com.paymentslab.core.data.di.dataModule
import com.paymentslab.core.network.di.networkModule
import com.paymentslab.core.orchestration.di.orchestrationModule
import com.paymentslab.feature.checkoutdemo.di.checkoutDemoModule
import com.paymentslab.feature.history.di.historyModule
import com.paymentslab.feature.home.di.homeModule
import com.paymentslab.feature.lab.di.labAiModule
import com.paymentslab.feature.lab.di.labModule
import com.siddharth.kmp.common.CrashReporter
import com.siddharth.kmp.common.NapierCrashReporter
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.provider.cash.di.cashModule
import com.siddharth.kmp.provider.cashfree.di.cashfreeModule
import com.siddharth.kmp.provider.flutterwave.di.flutterwaveModule
import com.siddharth.kmp.provider.googlepay.di.googlePayModule
import com.siddharth.kmp.provider.hostedwebview.di.hostedWebViewModule
import com.siddharth.kmp.provider.mobilemoney.di.mobileMoneyModule
import com.siddharth.kmp.provider.mpesa.di.mpesaModule
import com.siddharth.kmp.provider.nmi.di.nmiModule
import com.siddharth.kmp.provider.omise.di.omiseModule
import com.siddharth.kmp.provider.paystack.di.paystackModule
import com.siddharth.kmp.provider.paytm.di.paytmModule
import com.siddharth.kmp.provider.peach.di.peachModule
import com.siddharth.kmp.provider.razorpay.di.razorpayModule
import com.siddharth.kmp.provider.square.di.squareModule
import com.siddharth.kmp.provider.stripe.di.stripeModule
import com.siddharth.kmp.provider.stripeconnect.di.stripeConnectModule
import com.siddharth.kmp.provider.upiintent.di.upiIntentModule
import com.siddharth.kmp.provider.wallet.di.walletModule
import com.siddharth.kmp.provider.xendit.di.xenditModule
import com.siddharth.kmp.security.AppSecurityManager
import com.siddharth.kmp.security.SecurityAuditor
import com.siddharth.kmp.security.SecurityConfig
import com.siddharth.kmp.security.SecurityPolicy
import com.siddharth.kmp.security.SecurityPosture
import com.siddharth.kmp.security.di.securityModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

/**
 * Composition root. Assembles every module's Koin definitions in one place — the ONLY place that
 * knows the full graph. The gateway registry is built by `orchestrationModule` from the providers
 * contributed here via `getAll<PaymentGateway>()`, so enabling a provider is a one-line change here.
 *
 * Also the WorkManager [Configuration.Provider]: WorkManager initializes on demand with a factory
 * that can build the reconciliation worker from Koin (see [PaymentWorkerFactory]).
 */
class PaymentsLabApplication :
    Application(),
    Configuration.Provider,
    KoinComponent {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        // Crash/non-fatal reporting behind an interface (Napier default; Crashlytics/Sentry is a
        // one-line DI swap). Install the uncaught-exception handler + build-variant keys first.
        val crashReporter: CrashReporter = NapierCrashReporter()
        CrashReportingInitializer.install(
            crashReporter,
            mapOf("buildType" to BuildConfig.BUILD_TYPE, "versionName" to BuildConfig.VERSION_NAME),
        )

        // App-owned security policy: VAPT bypass flags come from BuildConfig (all false except in a
        // dedicated compliance-test variant).
        val securityConfig =
            SecurityConfig(
                bypassRoot = BuildConfig.BYPASS_ROOT,
                bypassHook = BuildConfig.BYPASS_HOOK,
                bypassSsl = BuildConfig.BYPASS_SSL,
                bypassDebugger = BuildConfig.BYPASS_DEBUGGER,
            )

        startKoin {
            androidContext(this@PaymentsLabApplication)
            modules(
                org.koin.dsl.module { single { crashReporter } },
                // core
                dataModule,
                networkModule(PaymentApiConfig(BuildConfig.BACKEND_URL)),
                orchestrationModule,
                securityModule(securityConfig),
                aiModule,
                // providers (each contributes a PaymentGateway into the registry)
                upiIntentModule,
                razorpayModule,
                paystackModule,
                flutterwaveModule,
                cashfreeModule,
                stripeModule,
                googlePayModule,
                squareModule,
                omiseModule,
                paytmModule,
                cashModule,
                peachModule,
                nmiModule,
                walletModule(listOf(walletConfig)),
                xenditModule(),
                mpesaModule(),
                mobileMoneyModule(
                    listOf(
                        mtnMomoConfig,
                        beyonicConfig,
                        orangeMoneyConfig,
                        waveConfig,
                        ecocashConfig,
                        easypaisaConfig,
                        vukapayConfig,
                    ),
                ),
                hostedWebViewModule(
                    listOf(
                        mollieHostedGatewayConfig,
                        culqiHostedGatewayConfig,
                        ozowHostedGatewayConfig,
                        sslcommerzHostedGatewayConfig,
                        bkashHostedGatewayConfig,
                        hyperpayHostedGatewayConfig,
                        telrHostedGatewayConfig,
                        myfatoorahHostedGatewayConfig,
                        paywayHostedGatewayConfig,
                        wipayHostedGatewayConfig,
                        paymarkHostedGatewayConfig,
                        vistamoneyHostedGatewayConfig,
                        cmiHostedGatewayConfig,
                        myposHostedGatewayConfig,
                        woyopayHostedGatewayConfig,
                        amoleHostedGatewayConfig,
                        placetopayHostedGatewayConfig,
                        paymentezHostedGatewayConfig,
                        webxpayHostedGatewayConfig,
                        cardnetHostedGatewayConfig,
                        kanooHostedGatewayConfig,
                        moncashHostedGatewayConfig,
                        jccHostedGatewayConfig,
                        truevoHostedGatewayConfig,
                        dotlinesHostedGatewayConfig,
                        expresspayHostedGatewayConfig,
                        factranzHostedGatewayConfig,
                        mobizpayHostedGatewayConfig,
                        smartpayHostedGatewayConfig,
                        thiwaniHostedGatewayConfig,
                        asapcardsHostedGatewayConfig,
                        arakaHostedGatewayConfig,
                        plugnpayHostedGatewayConfig,
                        savvyHostedGatewayConfig,
                        acceptcardHostedGatewayConfig,
                        phonepeHostedGatewayConfig,
                        worldpayHostedGatewayConfig,
                        payuHostedGatewayConfig,
                        ipay88HostedGatewayConfig,
                        twintHostedGatewayConfig,
                        paypalHostedGatewayConfig,
                        areebaHostedGatewayConfig,
                        conektaHostedGatewayConfig,
                        midtransHostedGatewayConfig,
                    ),
                ),
                stripeConnectModule,
                stubGatewayModule(
                    listOf(
                        cybersourceStubConfig,
                        selcomStubConfig,
                        supaGhanaPayStubConfig,
                    ),
                ),
                // features
                labModule,
                labAiModule,
                historyModule,
                checkoutDemoModule,
                homeModule,
            )
        }

        // Screen-facing defenses (FLAG_SECURE re-assert on background). Per-Activity screenshot +
        // tapjacking protection is applied in MainActivity.
        get<AppSecurityManager>().install(this)

        // VAPT audit on launch, off the main thread (file I/O + process spawn). Detection is separate
        // from enforcement: SecurityPolicy turns the audit into an action for the current posture
        // (strict in release, lenient in debug). Log-only here — a real app would gate card entry on
        // a BLOCK; that decision is intentionally the app's, not the detector's.
        CoroutineScope(Dispatchers.IO).launch {
            val audit = get<SecurityAuditor>().audit()
            val posture = if (BuildConfig.DEBUG) SecurityPosture.lenient() else SecurityPosture.strict()
            val decision = SecurityPolicy.evaluate(audit, posture)
            crashReporter.setCustomKey("security_decision", decision.action.name)
            when {
                decision.shouldBlock -> {
                    crashReporter.log("security BLOCK: ${decision.summary()}")
                    Napier.e("Security: BLOCK — ${decision.summary()}", tag = "Security")
                }
                decision.shouldWarn -> {
                    crashReporter.log("security WARN: ${decision.summary()}")
                    Napier.w("Security: WARN — ${decision.summary()}", tag = "Security")
                }
            }
        }

        // Process-death recovery safety net for any payment left pending.
        PaymentReconciliationWorker.enqueue(this)
    }

    // WorkManager on-demand init uses this — set after startKoin so the Koin graph is ready.
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(PaymentWorkerFactory(get()))
                .build()
}
