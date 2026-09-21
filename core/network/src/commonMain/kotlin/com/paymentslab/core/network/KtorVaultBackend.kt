package com.paymentslab.core.network

import com.paymentslab.core.protocol.ChargeInstrumentRequest
import com.paymentslab.core.protocol.InstrumentChargeResponse
import com.paymentslab.core.protocol.SaveInstrumentRequest
import com.paymentslab.core.protocol.SavedInstrumentDto
import com.paymentslab.core.protocol.SavedInstrumentsResponse
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.InstrumentCharge
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.SavedInstrument
import com.siddharth.kmp.paymentsapi.VaultBackend
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * The Ktor implementation of [VaultBackend] — mirrors [KtorPayoutBackend]'s shape (same
 * request-wrapping/error-mapping pattern) for the Stripe Customer + vault rail (roadmap #7).
 */
class KtorVaultBackend(
    private val client: HttpClient,
    private val config: PaymentApiConfig,
) : VaultBackend {
    private val base: String = config.baseUrl.trimEnd('/')

    override suspend fun save(
        customerId: String,
        cardToken: String,
        brand: String,
        last4: String,
        idempotencyKey: String,
    ): SavedInstrument =
        request("save(customer=$customerId, brand=$brand)") {
            val response: SavedInstrumentDto =
                client
                    .post("$base/vault/$customerId/instruments") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            SaveInstrumentRequest(
                                cardToken = cardToken,
                                brand = brand,
                                last4 = last4,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }.body()
            response.toDomain()
        }

    override suspend fun list(customerId: String): List<SavedInstrument> =
        request("list(customer=$customerId)") {
            val response: SavedInstrumentsResponse = client.get("$base/vault/$customerId/instruments").body()
            response.instruments.map { it.toDomain() }
        }

    override suspend fun charge(
        customerId: String,
        instrumentId: String,
        catalogItemId: String,
        idempotencyKey: String,
    ): InstrumentCharge =
        request("charge(customer=$customerId, instrument=$instrumentId)") {
            val response: InstrumentChargeResponse =
                client
                    .post("$base/vault/$customerId/instruments/$instrumentId/charge") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            ChargeInstrumentRequest(
                                catalogItemId = catalogItemId,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }.body()
            response.toDomain()
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
            throw PaymentNetworkException("Vault backend call failed: $label (${t.message})", t)
        }

    companion object {
        private const val TAG = "KtorVaultBackend"
    }
}

private fun SavedInstrumentDto.toDomain() =
    SavedInstrument(
        instrumentId = instrumentId,
        customerId = customerId,
        brand = brand,
        last4 = last4,
    )

private fun InstrumentChargeResponse.toDomain() =
    InstrumentCharge(
        chargeId = chargeId,
        customerId = customerId,
        instrumentId = instrumentId,
        amount = Money(amountMinor, currency),
        status = status.toDomain(),
    )
