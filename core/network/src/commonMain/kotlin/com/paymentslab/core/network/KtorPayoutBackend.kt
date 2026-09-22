package com.paymentslab.core.network

import com.paymentslab.core.protocol.InitiatePayoutRequest
import com.paymentslab.core.protocol.PayoutResponse
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PayoutBackend
import com.siddharth.kmp.paymentsapi.PayoutSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * The Ktor implementation of [PayoutBackend] — mirrors [KtorPaymentBackend]'s shape (same
 * request-wrapping/error-mapping pattern) for the Transfers/payout rail.
 */
class KtorPayoutBackend(
    private val client: HttpClient,
    private val config: PaymentApiConfig,
) : PayoutBackend {
    private val base: String = config.baseUrl.trimEnd('/')

    override suspend fun initiate(
        gatewayId: GatewayId,
        recipientRef: String,
        amount: Money,
        idempotencyKey: String,
    ): PayoutSnapshot =
        request("initiate(gateway=${gatewayId.value}, recipient=$recipientRef)") {
            val response: PayoutResponse =
                client
                    .post("$base/payouts") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            InitiatePayoutRequest(
                                gatewayId = gatewayId.value,
                                recipientRef = recipientRef,
                                amountMinor = amount.amountMinor,
                                currency = amount.currency,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }.body()
            response.toSnapshot()
        }

    override suspend fun status(payoutId: String): PayoutSnapshot =
        request("status(payoutId=$payoutId)") {
            val response: PayoutResponse = client.get("$base/payouts/$payoutId").body()
            response.toSnapshot()
        }

    // A transport boundary, so the catch has to be total: Ktor's throw surface is engine-specific
    // (OkHttp on Android, Darwin on iOS, fetch on wasmJs all raise different types) and there is no
    // portable narrower set to name. CancellationException is rethrown above so structured
    // concurrency still works; everything else becomes one domain exception callers can handle.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> request(
        label: String,
        block: () -> T,
    ): T =
        try {
            AppLog.d("-> $label", tag = TAG)
            block().also { AppLog.d("<- $label ok", tag = TAG) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppLog.e("x $label failed: ${t.message}", t, tag = TAG)
            throw PaymentNetworkException("Payout backend call failed: $label (${t.message})", t)
        }

    companion object {
        private const val TAG = "KtorPayoutBackend"
    }
}
