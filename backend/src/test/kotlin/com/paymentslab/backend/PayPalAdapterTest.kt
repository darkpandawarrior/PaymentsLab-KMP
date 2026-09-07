package com.paymentslab.backend

import com.paymentslab.core.config.CredentialMode
import com.paymentslab.core.config.GatewayCredentials
import com.paymentslab.core.protocol.CatalogItemDto
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.VerifyRequest
import com.siddharth.kmp.paymentsapi.GatewayId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * No live PayPal sandbox credentials were available to exercise the real path against the actual
 * API this session — these tests verify the request/response mapping against a mocked HTTP engine
 * instead (same approach as `PaystackAdapterTest`).
 */
class PayPalAdapterTest {
    private val item = CatalogItemDto("coffee_149", "Coffee", "desc", 14_900L, "INR")
    private val enabledCredentials =
        GatewayCredentials(
            GatewayId("paypal"),
            CredentialMode.TEST,
            mapOf("client_id" to "sb-client-id", "client_secret" to "sb-client-secret"),
            listOf("client_id", "client_secret"),
        )

    @Test
    fun `mock mode returns the generic mock checkout path and skips the network call`() =
        runTest {
            var called = false
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler {
                            called = true
                            respond("should not be called")
                        }
                    }
                }
            val adapter =
                PayPalAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paypal"),
                            CredentialMode.TEST,
                            emptyMap(),
                            listOf("client_id", "client_secret"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = client,
                )

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("http://10.0.2.2:8080/mock/checkout/paypal?orderId=order_1", params["checkout_url"])
            assertEquals(false, called)
        }

    @Test
    fun `real mode fetches an OAuth token then creates an order and returns the approve link`() =
        runTest {
            var sawAuthHeader: String? = null
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler { request ->
                            when {
                                request.url.encodedPath.endsWith("/oauth2/token") -> {
                                    sawAuthHeader = request.headers[HttpHeaders.Authorization]
                                    respond(
                                        content = """{"access_token":"sandbox-token-abc"}""",
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                }
                                request.url.encodedPath.endsWith("/checkout/orders") -> {
                                    assertEquals("Bearer sandbox-token-abc", request.headers[HttpHeaders.Authorization])
                                    respond(
                                        content =
                                            """{"id":"5O190127TN364715T","status":"CREATED","links":[
                                            |{"href":"https://api-m.sandbox.paypal.com/v2/checkout/orders/5O190127TN364715T","rel":"self"},
                                            |{"href":"https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T","rel":"approve"}
                                            |]}
                                            """.trimMargin(),
                                        status = HttpStatusCode.Created,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                }
                                else -> error("Unexpected request: ${request.url}")
                            }
                        }
                    }
                }
            val adapter = PayPalAdapter(enabledCredentials, "http://10.0.2.2:8080", client)

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T", params["checkout_url"])
            assertEquals("5O190127TN364715T", params["paypal_order_id"])
            assert(sawAuthHeader?.startsWith("Basic ") == true)
        }

    @Test
    fun `real mode verify captures the order and maps COMPLETED to SUCCESS`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler { request ->
                            when {
                                request.url.encodedPath.endsWith("/oauth2/token") ->
                                    respond(
                                        content = """{"access_token":"sandbox-token-abc"}""",
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                request.url.encodedPath.endsWith("/capture") ->
                                    respond(
                                        content = """{"id":"5O190127TN364715T","status":"COMPLETED"}""",
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                else -> error("Unexpected request: ${request.url}")
                            }
                        }
                    }
                }
            val adapter = PayPalAdapter(enabledCredentials, "http://10.0.2.2:8080", client)

            val status =
                adapter.verify(
                    VerifyRequest(gatewayId = "paypal", orderId = "order_1", paymentId = "5O190127TN364715T"),
                )

            assertEquals(PaymentStatusDto.SUCCESS, status)
        }

    @Test
    fun `mock mode verify is always pending`() =
        runTest {
            val adapter =
                PayPalAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paypal"),
                            CredentialMode.TEST,
                            emptyMap(),
                            listOf("client_id", "client_secret"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val status = adapter.verify(VerifyRequest(gatewayId = "paypal", orderId = "order_1", paymentId = null))

            assertEquals(PaymentStatusDto.PENDING, status)
            assertNull(VerifyRequest(gatewayId = "paypal", orderId = "order_1").paymentId)
        }
}
