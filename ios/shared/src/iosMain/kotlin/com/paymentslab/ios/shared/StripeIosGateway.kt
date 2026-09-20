package com.paymentslab.ios.shared

import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentPreparationException
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PreparedPayment
import com.siddharth.kmp.paymentsapi.Redactor
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Stripe on iOS — the native-SDK counterpart to Android's `provider:stripe` `StripeGateway`,
 * proving that gateway's Android-only status was a scoping choice (the Android module wraps
 * Stripe's Android SDK types directly, with no iOS target) and not a platform limitation: Stripe
 * genuinely ships a real iOS SDK (`stripe-ios` 26.11.0, via SPM), same as the Android one.
 *
 * The actual `PaymentSheet` call lives in Swift ([StripeCheckoutHost]'s implementation,
 * `StripeCheckoutHostImpl.swift`) since Kotlin/Native can't cinterop against a Swift-only
 * framework — Swift implements the Kotlin interface instead, the supported direction.
 *
 * `prepare`/`pay` and the result-mapping logic below are a deliberate near-mirror of Android's
 * `StripeGateway` — same server contract (`client_secret`/`publishable_key` from `POST /orders`,
 * the same `StripeAdapter` on the backend serves both platforms unmodified), same
 * client-result-is-a-hint discipline.
 */
class StripeIosGateway(
    private val checkoutHost: StripeCheckoutHost,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("stripe")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Stripe",
            status = GatewayStatus.SANDBOX_READY,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT, Capability.CARDS, Capability.WALLET),
            region = "Global",
            docsPath = "docs/providers/stripe.md",
            blurb =
                "Card checkout via Stripe PaymentSheet — the real iOS SDK, not a WebView fallback. " +
                    "Same PaymentIntent contract as the Android StripeGateway.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val params = created.providerParams
        val clientSecret =
            params[KEY_CLIENT_SECRET]
                ?: throw PaymentPreparationException("Stripe order missing '$KEY_CLIENT_SECRET'")
        val publishableKey =
            params[KEY_PUBLISHABLE_KEY]
                ?: throw PaymentPreparationException("Stripe order missing '$KEY_PUBLISHABLE_KEY'")
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = mapOf(KEY_CLIENT_SECRET to clientSecret, KEY_PUBLISHABLE_KEY to publishableKey),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val clientSecret =
            prepared.params[KEY_CLIENT_SECRET]
                ?: return configMissing("Stripe payment missing '$KEY_CLIENT_SECRET'")
        val publishableKey =
            prepared.params[KEY_PUBLISHABLE_KEY]
                ?: return configMissing("Stripe payment missing '$KEY_PUBLISHABLE_KEY'")

        return suspendCancellableCoroutine { cont ->
            checkoutHost.presentPaymentSheet(clientSecret, publishableKey, MERCHANT_DISPLAY_NAME) { outcome ->
                if (cont.isActive) cont.resume(outcome.toPaymentResult(clientSecret)) { _, _, _ -> }
            }
        }
    }

    private fun StripeCheckoutOutcome.toPaymentResult(clientSecret: String): PaymentResult =
        when (this) {
            is StripeCheckoutOutcome.Completed ->
                PaymentResult.Success(
                    paymentId = paymentIntentId,
                    verification = mapOf("payment_intent" to paymentIntentId, "client_secret_present" to "true"),
                    raw =
                        Redactor.redact(
                            "stripe.paymentsheet.completed",
                            mapOf(
                                "status" to "completed",
                                "payment_intent" to paymentIntentId,
                                "client_secret" to clientSecret,
                            ),
                        ),
                )

            StripeCheckoutOutcome.Canceled ->
                PaymentResult.Cancelled(
                    raw = Redactor.redact("stripe.paymentsheet.canceled", mapOf("status" to "canceled")),
                )

            is StripeCheckoutOutcome.Failed ->
                PaymentResult.Failure(
                    code = FailureCode.GATEWAY_DECLINED,
                    message = UiText.of(message),
                    raw =
                        Redactor.redact(
                            "stripe.paymentsheet.failed",
                            mapOf("status" to "failed", "error" to message),
                        ),
                )
        }

    private fun configMissing(message: String): PaymentResult.Failure =
        PaymentResult.Failure(
            code = FailureCode.CONFIG_MISSING,
            message = UiText.of(message),
            raw = Redactor.redact("stripe.config.error", mapOf("error" to message)),
        )

    private companion object {
        const val MERCHANT_DISPLAY_NAME = "PaymentsLab-KMP"
        const val KEY_CLIENT_SECRET = "client_secret"
        const val KEY_PUBLISHABLE_KEY = "publishable_key"
    }
}
