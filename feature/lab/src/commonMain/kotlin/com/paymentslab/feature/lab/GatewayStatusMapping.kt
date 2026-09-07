package com.paymentslab.feature.lab

import com.paymentslab.core.designsystem.GatewayStatusUi
import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.siddharth.kmp.paymentsapi.FailureCode
import com.siddharth.kmp.paymentsapi.GatewayStatus

/** 1:1 mapping of the domain [GatewayStatus] onto the design-system [GatewayStatusUi]. */
internal fun GatewayStatus.toUi(): GatewayStatusUi =
    when (this) {
        GatewayStatus.SANDBOX_READY -> GatewayStatusUi.SANDBOX_READY
        GatewayStatus.MOCK_MODE -> GatewayStatusUi.MOCK_MODE
        GatewayStatus.KYC_GATED -> GatewayStatusUi.KYC_GATED
        GatewayStatus.COMING_SOON -> GatewayStatusUi.COMING_SOON
    }

/**
 * Deterministic, no-network floor for [com.paymentslab.feature.lab.explain.ErrorExplainer]: a
 * plain-language read of the client SDK's normalized [FailureCode] — this always renders, with no
 * key and no network, before (or instead of) anything a model adds on top.
 *
 * Grounded by [phase] — the [PaymentFsm][com.paymentslab.core.orchestration.fsm] state the failure
 * was observed in — because the same [FailureCode] means something different mid-flow than once
 * settled: a [FailureCode.GATEWAY_DECLINED] seen while [PaymentPhase.VERIFYING] is still just the
 * SDK's own claim, being re-checked against the server; the same code once [PaymentPhase.TERMINAL]
 * means the server has actually confirmed the decline.
 */
internal fun FailureCode.toPlainExplanation(phase: PaymentPhase): String =
    when (this) {
        FailureCode.USER_CANCELLED ->
            "You cancelled the payment before it completed. No charge was made."
        FailureCode.NETWORK_ERROR ->
            "The connection dropped before the gateway could respond. This is usually a flaky " +
                "network, not the payment itself — retrying often succeeds."
        FailureCode.GATEWAY_DECLINED ->
            if (phase == PaymentPhase.TERMINAL) {
                "The issuing bank declined this payment. This is commonly insufficient funds, a " +
                    "blocked or expired card, or a bank-side fraud check — try a different card or " +
                    "payment method, or add 3D-Secure if the gateway supports it."
            } else {
                "The gateway's SDK reported a decline. The server is still confirming this — a " +
                    "client-side report can occasionally be wrong, so this isn't final yet."
            }
        FailureCode.VERIFICATION_FAILED ->
            "The server could not verify the SDK's signature on this result. Treated as failed " +
                "rather than trusted, since an unverified client claim is never good enough here."
        FailureCode.CONFIG_MISSING ->
            "This gateway is missing required configuration (a key, order id, or similar) — an " +
                "integration problem, not something the payer caused."
        FailureCode.SDK_ERROR ->
            "The provider's SDK failed to run (it couldn't open, or crashed) rather than the " +
                "payment itself being declined."
    }
