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
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * No live Square sandbox credentials were available to exercise the real path against the actual
 * API this session — these tests verify the request/response mapping against a mocked HTTP engine
 * instead (same approach as `PayPalAdapterTest`).
 */
class SquareAdapterTest {
    private val item = CatalogItemDto("coffee_149", "Coffee", "desc", 14_900L, "USD")
    private val enabledCredentials =
        GatewayCredentials(
            GatewayId("square"),
            CredentialMode.TEST,
            mapOf(
                "application_id" to "sandbox-sq0idb-fake",
                "access_token" to "sandbox-access-token",
                "location_id" to "L_FAKE",
            ),
            listOf("application_id", "access_token", "location_id"),
        )
    private val disabledCredentials =
        GatewayCredentials(
            GatewayId("square"),
            CredentialMode.TEST,
            emptyMap(),
            listOf("application_id", "access_token", "location_id"),
        )

    @Test
    fun `mock mode returns no provider params and skips the network call`() =
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
            val adapter = SquareAdapter(disabledCredentials, client)

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals(emptyMap<String, String>(), params)
            assertFalse(called)
        }

    @Test
    fun `mock mode verify is always pending`() =
        runTest {
            val adapter =
                SquareAdapter(
                    disabledCredentials,
                    HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val status =
                adapter.verify(VerifyRequest(gatewayId = "square", orderId = "order_1", paymentId = "nonce_abc"))

            assertEquals(PaymentStatusDto.PENDING, status)
        }

    @Test
    fun `real mode createProviderOrder returns the application id`() =
        runTest {
            val adapter =
                SquareAdapter(
                    enabledCredentials,
                    HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("sandbox-sq0idb-fake", params["application_id"])
        }

    @Test
    fun `real mode verify charges the nonce and maps COMPLETED to SUCCESS`() =
        runTest {
            var sawAuthHeader: String? = null
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler { request ->
                            sawAuthHeader = request.headers[HttpHeaders.Authorization]
                            respond(
                                content = """{"payment":{"id":"pay_abc","status":"COMPLETED"}}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val adapter = SquareAdapter(enabledCredentials, client)
            adapter.createProviderOrder("order_1", item)

            val status =
                adapter.verify(VerifyRequest(gatewayId = "square", orderId = "order_1", paymentId = "nonce_abc"))

            assertEquals(PaymentStatusDto.SUCCESS, status)
            assertEquals("Bearer sandbox-access-token", sawAuthHeader)
        }

    @Test
    fun `real mode verify fails when no order was cached for this orderId`() =
        runTest {
            val adapter =
                SquareAdapter(
                    enabledCredentials,
                    HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val status =
                adapter.verify(VerifyRequest(gatewayId = "square", orderId = "unknown_order", paymentId = "nonce_abc"))

            assertEquals(PaymentStatusDto.FAILED, status)
        }
}
