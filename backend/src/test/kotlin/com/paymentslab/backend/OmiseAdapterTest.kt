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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * No live Omise sandbox credentials were available to exercise the real path against the actual
 * API this session — these tests verify the request/response mapping against a mocked HTTP engine
 * instead (same approach as `SquareAdapterTest`).
 */
class OmiseAdapterTest {
    private val item = CatalogItemDto("coffee_149", "Coffee", "desc", 14_900L, "THB")
    private val enabledCredentials =
        GatewayCredentials(
            GatewayId("omise"),
            CredentialMode.TEST,
            mapOf("public_key" to "pkey_test_fake", "secret_key" to "skey_test_fake"),
            listOf("public_key", "secret_key"),
        )
    private val disabledCredentials =
        GatewayCredentials(
            GatewayId("omise"),
            CredentialMode.TEST,
            emptyMap(),
            listOf("public_key", "secret_key"),
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
            val adapter = OmiseAdapter(disabledCredentials, client)

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals(emptyMap<String, String>(), params)
            assertFalse(called)
        }

    @Test
    fun `mock mode verify is always pending`() =
        runTest {
            val adapter =
                OmiseAdapter(
                    disabledCredentials,
                    HttpClient(MockEngine) { engine { addHandler { respond("unused") } } },
                )

            val status = adapter.verify(VerifyRequest(gatewayId = "omise", orderId = "order_1", paymentId = "tokn_abc"))

            assertEquals(PaymentStatusDto.PENDING, status)
        }

    @Test
    fun `real mode createProviderOrder returns the public key`() =
        runTest {
            val adapter =
                OmiseAdapter(enabledCredentials, HttpClient(MockEngine) { engine { addHandler { respond("unused") } } })

            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("pkey_test_fake", params["public_key"])
        }

    @Test
    fun `real mode verify charges the token with basic auth and maps successful to SUCCESS`() =
        runTest {
            var sawAuthHeader: String? = null
            val client =
                HttpClient(MockEngine) {
                    install(ContentNegotiation) { json(BackendJson) }
                    engine {
                        addHandler { request ->
                            sawAuthHeader = request.headers[HttpHeaders.Authorization]
                            respond(
                                content = """{"id":"chrg_abc","status":"successful","paid":true}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val adapter = OmiseAdapter(enabledCredentials, client)
            adapter.createProviderOrder("order_1", item)

            val status = adapter.verify(VerifyRequest(gatewayId = "omise", orderId = "order_1", paymentId = "tokn_abc"))

            assertEquals(PaymentStatusDto.SUCCESS, status)
            val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("skey_test_fake:".toByteArray())
            assertEquals(expectedAuth, sawAuthHeader)
        }

    @Test
    fun `real mode verify fails when no order was cached for this orderId`() =
        runTest {
            val adapter =
                OmiseAdapter(enabledCredentials, HttpClient(MockEngine) { engine { addHandler { respond("unused") } } })

            val status =
                adapter.verify(VerifyRequest(gatewayId = "omise", orderId = "unknown_order", paymentId = "tokn_abc"))

            assertTrue(status == PaymentStatusDto.FAILED)
        }
}
