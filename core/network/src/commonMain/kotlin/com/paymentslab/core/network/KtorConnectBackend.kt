package com.paymentslab.core.network

import com.paymentslab.core.protocol.ConnectAccountResponse
import com.paymentslab.core.protocol.ConnectAccountStatusDto
import com.paymentslab.core.protocol.ConnectOnboardResponse
import com.paymentslab.core.protocol.ConnectPayoutRequest
import com.paymentslab.core.protocol.PayoutResponse
import com.paymentslab.core.protocol.PayoutStatusDto
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.paymentsapi.ConnectAccount
import com.siddharth.kmp.paymentsapi.ConnectAccountStatus
import com.siddharth.kmp.paymentsapi.ConnectBackend
import com.siddharth.kmp.paymentsapi.ConnectOnboarding
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentApiConfig
import com.siddharth.kmp.paymentsapi.PayoutSnapshot
import com.siddharth.kmp.paymentsapi.PayoutStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * The Ktor implementation of [ConnectBackend] — mirrors [KtorPayoutBackend]'s shape (same
 * request-wrapping/error-mapping pattern) for the Stripe Connect payout onboarding rail.
 */
class KtorConnectBackend(
    private val client: HttpClient,
    private val config: PaymentApiConfig,
) : ConnectBackend {
    private val base: String = config.baseUrl.trimEnd('/')

    override suspend fun onboard(): ConnectOnboarding =
        request("onboard()") {
            val response: ConnectOnboardResponse = client.post("$base/connect/onboard").body()
            ConnectOnboarding(
                onboardingId = response.onboardingId,
                hostedOAuthUrl = response.hostedOAuthUrl,
                accountId = response.accountId,
            )
        }

    override suspend fun completeOnboarding(onboardingId: String): ConnectAccount =
        request("completeOnboarding(onboardingId=$onboardingId)") {
            val response: ConnectAccountResponse =
                client.post("$base/mock/connect/$onboardingId/complete").body()
            response.toAccount()
        }

    override suspend fun status(accountId: String): ConnectAccount =
        request("status(accountId=$accountId)") {
            val response: ConnectAccountResponse = client.get("$base/connect/$accountId").body()
            response.toAccount()
        }

    override suspend fun payout(
        accountId: String,
        amount: Money,
        idempotencyKey: String,
    ): PayoutSnapshot =
        request("payout(accountId=$accountId)") {
            val response: PayoutResponse =
                client
                    .post("$base/connect/$accountId/payouts") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            ConnectPayoutRequest(
                                amountMinor = amount.amountMinor,
                                currency = amount.currency,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }.body()
            PayoutSnapshot(
                payoutId = response.payoutId,
                gatewayId = GatewayId(response.gatewayId),
                recipientRef = response.recipientRef,
                amount = Money(response.amountMinor, response.currency),
                status = response.status.toDomain(),
            )
        }

    private fun ConnectAccountResponse.toAccount() =
        ConnectAccount(
            accountId = accountId,
            status =
                when (status) {
                    ConnectAccountStatusDto.ONBOARDING_PENDING -> ConnectAccountStatus.ONBOARDING_PENDING
                    ConnectAccountStatusDto.CONNECTED -> ConnectAccountStatus.CONNECTED
                },
        )

    private fun PayoutStatusDto.toDomain() =
        when (this) {
            PayoutStatusDto.PENDING -> PayoutStatus.PENDING
            PayoutStatusDto.SETTLED -> PayoutStatus.SETTLED
            PayoutStatusDto.FAILED -> PayoutStatus.FAILED
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
            throw PaymentNetworkException("Connect backend call failed: $label (${t.message})", t)
        }

    companion object {
        private const val TAG = "KtorConnectBackend"
    }
}
