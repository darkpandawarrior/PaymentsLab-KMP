package com.paymentslab.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.paymentslab.core.designsystem.LocalReducedMotion
import com.paymentslab.core.designsystem.PaymentsLabTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.provider.cashfree.CashfreeCheckoutRelay
import com.siddharth.kmp.provider.razorpay.RazorpayCallbackRelay
import com.siddharth.kmp.provider.razorpay.RazorpayCallbackResult
import com.siddharth.kmp.provider.square.SquareCallbackRelay
import com.siddharth.kmp.provider.square.SquareCallbackResult
import com.siddharth.kmp.provider.stripe.StripePaymentLauncherHost
import com.siddharth.kmp.security.AppSecurityManager
import com.stripe.android.paymentsheet.rememberPaymentSheet
import org.koin.android.ext.android.inject
import sqip.Callback
import sqip.CardEntry
import sqip.CardEntryActivityResult

/**
 * The single launcher Activity — and the one place the Activity-callback-era gateway SDKs are
 * allowed to touch. It owns nothing about payment logic; it only forwards each SDK's Activity-level
 * callback into that provider's coroutine bridge:
 *
 *  - **Razorpay** requires the Activity itself to implement [PaymentResultWithDataListener]; results
 *    are flattened (no Razorpay types escape) and pushed to [RazorpayCallbackRelay].
 *  - **Cashfree** requires [CFCheckoutResponseCallback] set on the gateway service in `onCreate`;
 *    results go to the injected [CashfreeCheckoutRelay].
 *  - **Stripe** needs its `PaymentSheet` built in Compose scope; the sheet's result callback is
 *    routed to the injected [StripePaymentLauncherHost].
 *  - **Square** predates `ActivityResultContract`s entirely — `CardEntry.startCardEntryActivity`
 *    calls `startActivityForResult` directly, so results land in the legacy [onActivityResult]
 *    override below, which forwards to `CardEntry.handleActivityResult` and then [SquareCallbackRelay].
 *
 * Gateways reach this Activity only through the opaque [com.siddharth.kmp.paymentsapi.PaymentHost]
 * ([PaymentHostController]); they never hold a reference to it.
 */
class MainActivity :
    ComponentActivity(),
    PaymentResultWithDataListener,
    CFCheckoutResponseCallback {
    private val stripeHost: StripePaymentLauncherHost by inject()
    private val cashfreeRelay: CashfreeCheckoutRelay by inject()
    private val appSecurity: AppSecurityManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // VAPT screen defenses: FLAG_SECURE (no screenshots/recording) + tapjacking (drop obscured
        // touches). Whole-activity here; SecureScreen additionally guards the payment routes.
        appSecurity.applySecurityToActivity(this)

        // Cashfree re-attaches its callback on recreation, so this must run in onCreate.
        runCatching { CFPaymentGatewayService.getInstance().setCheckoutCallback(this) }
            .onFailure { AppLog.w("Cashfree callback wiring failed", it, tag = TAG) }

        val host = PaymentHostController(this)

        setContent {
            PaymentsLabTheme(displayFontFamily = SpaceGrotesk) {
                // Stripe's PaymentSheet must be created in Compose scope (it registers an
                // ActivityResultLauncher before STARTED); route its result into the provider bridge.
                val paymentSheet = rememberPaymentSheet { result -> stripeHost.onResult(result) }
                LaunchedEffect(paymentSheet) {
                    stripeHost.attach { clientSecret, configuration ->
                        paymentSheet.presentWithPaymentIntent(clientSecret, configuration)
                    }
                }
                DisposableEffect(Unit) { onDispose { stripeHost.detach() } }

                // "Remove animations" (animator scale 0) is the platform reduce-motion signal. Read
                // once per composition root; a change mid-session applies on the next launch.
                val reducedMotion =
                    remember {
                        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    }
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    AppNavHost(paymentHost = host)
                }
            }
        }
    }

    // Belt-and-suspenders tapjacking guard: swallow touches delivered while an overlay is on top,
    // in addition to the view-level filterTouchesWhenObscured set by applySecurityToActivity.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (appSecurity.shouldBlockObscuredTouch(ev)) return false
        return super.dispatchTouchEvent(ev)
    }

    // ── Square (legacy startActivityForResult/onActivityResult) ─────────────
    @Deprecated("Square's CardEntry SDK predates ActivityResultContract; no alternative exists.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        CardEntry.handleActivityResult(
            data,
            object : Callback<CardEntryActivityResult> {
                override fun onResult(result: CardEntryActivityResult) {
                    when {
                        result.isSuccess() ->
                            SquareCallbackRelay.emit(
                                SquareCallbackResult.Success(
                                    nonce = result.getSuccessValue().nonce,
                                    cardLastFour = result.getSuccessValue().card.lastFourDigits,
                                ),
                            )
                        result.isCanceled() -> SquareCallbackRelay.emit(SquareCallbackResult.Canceled)
                    }
                }
            },
        )
    }

    // ── Razorpay (PaymentResultWithDataListener) ────────────────────────────
    override fun onPaymentSuccess(
        razorpayPaymentId: String?,
        paymentData: PaymentData?,
    ) {
        val data = paymentData?.data
        RazorpayCallbackRelay.emit(
            RazorpayCallbackResult.Success(
                razorpayPaymentId = razorpayPaymentId ?: paymentData?.paymentId,
                razorpayOrderId = data?.optString("razorpay_order_id")?.ifBlank { null },
                razorpaySignature = paymentData?.signature ?: data?.optString("razorpay_signature")?.ifBlank { null },
            ),
        )
    }

    override fun onPaymentError(
        code: Int,
        response: String?,
        paymentData: PaymentData?,
    ) {
        RazorpayCallbackRelay.emit(RazorpayCallbackResult.Error(code = code, description = response))
    }

    // ── Cashfree (CFCheckoutResponseCallback) ───────────────────────────────
    override fun onPaymentVerify(orderId: String) {
        cashfreeRelay.onPaymentVerify(orderId)
    }

    override fun onPaymentFailure(
        errorResponse: CFErrorResponse,
        orderId: String,
    ) {
        cashfreeRelay.onPaymentFailure(
            orderId = orderId,
            errorMessage = errorResponse.message,
            errorCode = errorResponse.code,
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
