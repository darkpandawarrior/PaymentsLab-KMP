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
 * Razorpay on iOS — the native-SDK counterpart to Android's `provider:razorpay` `RazorpayGateway`,
 * built on the real `RazorpayCheckout` iOS SDK (SPM, `razorpay-pod` `1.5.8`) rather than a WebView
 * fallback. Same boundary shape as [StripeIosGateway]: Kotlin/Native can't cinterop against
 * Razorpay's Swift-facing API surface directly (the SDK's public headers are Swift-generated), so
 * Swift implements the Kotlin [RazorpayCheckoutHost] interface instead.
 *
 * Same server contract as Android — `key_id`/`order_id` from `POST /orders`, the existing
 * `RazorpayAdapter`'s real HMAC-SHA256 signature verification serves both platforms unmodified.
 */
class RazorpayIosGateway(
    private val checkoutHost: RazorpayCheckoutHost,
) : PaymentGateway {
    override val id: GatewayId = GatewayId("razorpay")

    override val meta: GatewayMeta =
        GatewayMeta(
            displayName = "Razorpay",
            status = GatewayStatus.SANDBOX_READY,
            capabilities =
                setOf(
                    Capability.ONE_TIME_PAYMENT,
                    Capability.CARDS,
                    Capability.UPI,
                    Capability.NET_BANKING,
                    Capability.WALLET,
                ),
            region = "India",
            docsPath = "docs/providers/razorpay-ios.md",
            blurb =
                "Standard Checkout via the real Razorpay iOS SDK — same order_id/HMAC-SHA256 " +
                    "contract as the Android RazorpayGateway.",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment {
        val params = created.providerParams
        val keyId = params["key_id"] ?: throw PaymentPreparationException("Razorpay order missing 'key_id'")
        val orderId = params["order_id"] ?: throw PaymentPreparationException("Razorpay order missing 'order_id'")
        return PreparedPayment(
            gatewayId = id,
            orderId = created.order.orderId,
            amount = created.order.amount,
            params = mapOf("key_id" to keyId, "order_id" to orderId),
        )
    }

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult {
        val keyId = prepared.params["key_id"] ?: return configMissing("Razorpay payment missing 'key_id'")
        val orderId = prepared.params["order_id"] ?: return configMissing("Razorpay payment missing 'order_id'")

        return suspendCancellableCoroutine { cont ->
            checkoutHost.openCheckout(
                keyId,
                orderId,
                prepared.amount.amountMinor,
                prepared.amount.currency,
            ) { outcome ->
                if (cont.isActive) cont.resume(outcome.toPaymentResult()) { _, _, _ -> }
            }
        }
    }

    private fun RazorpayCheckoutOutcome.toPaymentResult(): PaymentResult =
        when (this) {
            is RazorpayCheckoutOutcome.Success ->
                PaymentResult.Success(
                    paymentId = paymentId,
                    // Unredacted, server-bound: the backend's HMAC verify needs order_id + signature.
                    verification =
                        mapOf(
                            "razorpay_order_id" to razorpayOrderId.orEmpty(),
                            "razorpay_signature" to razorpaySignature.orEmpty(),
                        ),
                    raw = Redactor.redact("razorpay.checkout.success", mapOf("payment_id" to paymentId)),
                )

            is RazorpayCheckoutOutcome.Error ->
                PaymentResult.Failure(
                    code = FailureCode.GATEWAY_DECLINED,
                    message = UiText.of(description),
                    raw =
                        Redactor.redact(
                            "razorpay.checkout.error",
                            mapOf("code" to code.toString(), "description" to description),
                        ),
                )
        }

    private fun configMissing(message: String): PaymentResult.Failure =
        PaymentResult.Failure(
            code = FailureCode.CONFIG_MISSING,
            message = UiText.of(message),
            raw = Redactor.redact("razorpay.config.error", mapOf("error" to message)),
        )
}
