package com.paymentslab.core.network

import com.paymentslab.core.protocol.OrderResponse
import com.paymentslab.core.protocol.PaymentStatusResponse
import com.paymentslab.core.protocol.VerifyResponse
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PaymentBackend
import com.siddharth.kmp.paymentsapi.PaymentSnapshot
import com.siddharth.kmp.paymentsapi.VerificationRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * The Ktor implementation of [PaymentBackend] — the app's only door to the `backend/` server.
 *
 * It speaks `core:protocol` DTOs on the wire and maps them to `core:payments-api` domain types via
 * [DtoMappers], so the orchestrator that consumes this never sees a DTO or an HTTP status. Any
 * transport/serialization failure is wrapped in [PaymentNetworkException] with a clear message; the
 * orchestrator maps that to an `Errored` payment.
 *
 * @param client the shared Ktor client (see `:network`'s `createHttpClient`).
 * @param config where the backend lives (see [PaymentApiConfig]).
 */
class KtorPaymentBackend(
    private val client: HttpClient,
    private val config: PaymentApiConfig,
) : PaymentBackend {
    private val base: String = config.baseUrl.trimEnd('/')

    override suspend fun createOrder(
        catalogItemId: String,
        gatewayId: GatewayId,
        idempotencyKey: String,
    ): CreatedOrder =
        request("createOrder(catalogItemId=$catalogItemId, gateway=${gatewayId.value})") {
            val response: OrderResponse =
                client
                    .post("$base/orders") {
                        contentType(ContentType.Application.Json)
                        setBody(createOrderRequest(catalogItemId, gatewayId, idempotencyKey))
                    }.body()
            response.toDomain()
        }

    override suspend fun verify(request: VerificationRequest): PaymentSnapshot =
        request("verify(orderId=${request.orderId})") {
            val response: VerifyResponse =
                client
                    .post("$base/payments/${request.orderId}/verify") {
                        contentType(ContentType.Application.Json)
                        setBody(request.toDto())
                    }.body()
            response.toSnapshot(orderId = request.orderId)
        }

    override suspend fun status(orderId: String): PaymentSnapshot =
        request("status(orderId=$orderId)") {
            val response: PaymentStatusResponse = client.get("$base/payments/$orderId").body()
            response.toSnapshot()
        }

    /**
     * Runs [block], logging entry/exit and converting any non-cancellation failure into a
     * [PaymentNetworkException] carrying [label] for context. [CancellationException] is rethrown
     * untouched so structured concurrency / cooperative cancellation still works.
     *
     * A transport boundary, so the catch has to be total: Ktor's throw surface is engine-specific
     * (OkHttp on Android, Darwin on iOS, fetch on wasmJs all raise different types) and there is no
     * portable narrower set to name — hence the suppression rather than a list of exception types.
     */
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
            throw PaymentNetworkException("Payment backend call failed: $label (${t.message})", t)
        }

    companion object {
        private const val TAG = "KtorPaymentBackend"
    }
}
