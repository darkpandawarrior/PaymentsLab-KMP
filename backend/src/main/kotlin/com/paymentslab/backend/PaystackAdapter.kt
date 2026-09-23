package com.paymentslab.backend

import com.paymentslab.core.config.GatewayCredentials
import com.paymentslab.core.protocol.CatalogItemDto
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.VerifyRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paystack — the B1 vertical-slice flagship, riding archetype C (hosted checkout) end-to-end.
 *
 * `credentials.enabled` (from `core:config`, `PLAB_PAYSTACK_TEST_SECRET_KEY`) decides real vs mock:
 * - **Real** (keys configured): `POST /transaction/initialize` mints a genuine Paystack
 *   `authorization_url` the client WebView opens; `GET /transaction/verify/{reference}` is the
 *   server-authoritative truth. Paystack's own checkout ALWAYS redirects to one `callback_url`
 *   regardless of outcome (`?reference=...`, no separate success/fail URL) — the redirect landing on
 *   `/mock/return/success` is therefore just a page label, not a security decision; only `verify()`
 *   decides SUCCESS/FAILED. This mirrors [UpiIntentAdapter]'s "client result is a hint" honesty.
 * - **Mock** (no keys — the default until real sandbox keys are supplied): falls back to the same
 *   `GET /mock/checkout/{provider}` generic path every other `MOCK_MODE` hosted gateway rides.
 *
 * Not yet exercised against the live API in this repo (no test keys were available to verify
 * against) — the request/response mapping is covered by [backend's PaystackAdapterTest] using a
 * mocked HTTP engine instead. `docs: https://paystack.com/docs/payments/accept-payments/`
 */
class PaystackAdapter(
    private val credentials: GatewayCredentials,
    private val publicBaseUrl: String,
    private val httpClient: HttpClient,
) : GatewayAdapter {
    override val gatewayId: String = "paystack"

    override suspend fun createProviderOrder(
        orderId: String,
        item: CatalogItemDto,
    ): Map<String, String> {
        if (!credentials.enabled) {
            return mapOf("checkout_url" to "$publicBaseUrl/mock/checkout/paystack?orderId=$orderId")
        }
        val secretKey = credentials.keys.getValue("secret_key")
        val response: PaystackInitializeResponse =
            httpClient
                .post("https://api.paystack.co/transaction/initialize") {
                    header(HttpHeaders.Authorization, "Bearer $secretKey")
                    contentType(ContentType.Application.Json)
                    setBody(
                        PaystackInitializeRequest(
                            email = "sandbox@paymentslab-kmp.example",
                            amount = item.amountMinor.toString(),
                            currency = item.currency,
                            reference = orderId,
                            callbackUrl = "$publicBaseUrl/mock/return/success",
                        ),
                    )
                }.body()
        return mapOf("checkout_url" to response.data.authorizationUrl, "reference" to response.data.reference)
    }

    override suspend fun verify(req: VerifyRequest): PaymentStatusDto {
        if (!credentials.enabled) return PaymentStatusDto.PENDING
        val reference = req.paymentId ?: return PaymentStatusDto.FAILED
        val secretKey = credentials.keys.getValue("secret_key")
        val response: PaystackVerifyResponse =
            httpClient
                .get("https://api.paystack.co/transaction/verify/$reference") {
                    header(HttpHeaders.Authorization, "Bearer $secretKey")
                }.body()
        return when (response.data.status) {
            "success" -> PaymentStatusDto.SUCCESS
            "failed", "abandoned" -> PaymentStatusDto.FAILED
            else -> PaymentStatusDto.PENDING
        }
    }
}

@Serializable
data class PaystackInitializeRequest(
    val email: String,
    val amount: String,
    val currency: String,
    val reference: String,
    @SerialName("callback_url") val callbackUrl: String,
)

@Serializable
data class PaystackInitializeResponse(
    val status: Boolean,
    val message: String,
    val data: PaystackInitializeData,
)

@Serializable
data class PaystackInitializeData(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("access_code") val accessCode: String,
    val reference: String,
)

@Serializable
data class PaystackVerifyResponse(
    val status: Boolean,
    val message: String,
    val data: PaystackVerifyData,
)

@Serializable
data class PaystackVerifyData(
    val status: String,
)
