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
 * No live Paystack test key was available to exercise the real path against the actual API — these
 * tests verify the request/response mapping against a mocked HTTP engine instead, so the integration
 * logic is genuinely checked rather than just trusted on read.
 */
class PaystackAdapterTest {
    private val item = CatalogItemDto("coffee_149", "Coffee", "desc", 14_900L, "INR")

    @Test
    fun `mock mode (no credentials) returns the generic mock checkout path and skips the network call`() =
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
                PaystackAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paystack"),
                            CredentialMode.TEST,
                            emptyMap(),
                            listOf("secret_key"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = client,
                )

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("http://10.0.2.2:8080/mock/checkout/paystack?orderId=order_1", params["checkout_url"])
            assertEquals(false, called)
        }

    @Test
    fun `real mode initializes a transaction and returns the authorization url`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler { request ->
                            assertEquals("Bearer sk_test_abc", request.headers[HttpHeaders.Authorization])
                            respond(
                                content =
                                    """{"status":true,"message":"ok","data":{""" +
                                        """"authorization_url":"https://checkout.paystack.com/abc123",""" +
                                        """"access_code":"abc123","reference":"order_1"}}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val adapter =
                PaystackAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paystack"),
                            CredentialMode.TEST,
                            mapOf("secret_key" to "sk_test_abc"),
                            listOf("secret_key"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = client,
                )

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("https://checkout.paystack.com/abc123", params["checkout_url"])
            assertEquals("order_1", params["reference"])
        }

    @Test
    fun `real mode verify maps paystack status to PaymentStatusDto`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler {
                            respond(
                                content = """{"status":true,"message":"ok","data":{"status":"success"}}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val adapter =
                PaystackAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paystack"),
                            CredentialMode.TEST,
                            mapOf("secret_key" to "sk_test_abc"),
                            listOf("secret_key"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = client,
                )

            val status =
                adapter.verify(
                    VerifyRequest(gatewayId = "paystack", orderId = "order_1", paymentId = "order_1"),
                )

            assertEquals(PaymentStatusDto.SUCCESS, status)
        }

    @Test
    fun `mock mode verify is always pending`() =
        runTest {
            val adapter =
                PaystackAdapter(
                    credentials =
                        GatewayCredentials(
                            GatewayId("paystack"),
                            CredentialMode.TEST,
                            emptyMap(),
                            listOf("secret_key"),
                        ),
                    publicBaseUrl = "http://10.0.2.2:8080",
                    httpClient = HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val status = adapter.verify(VerifyRequest(gatewayId = "paystack", orderId = "order_1", paymentId = null))

            assertEquals(PaymentStatusDto.PENDING, status)
            assertNull(VerifyRequest(gatewayId = "paystack", orderId = "order_1").paymentId)
        }
}
