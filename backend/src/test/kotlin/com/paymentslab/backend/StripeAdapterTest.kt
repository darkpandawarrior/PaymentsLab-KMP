package com.paymentslab.backend

import com.paymentslab.core.protocol.CatalogItemDto
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.VerifyRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [StripeAdapter] has no real network call at all — both `createProviderOrder` and `verify` are
 * explicit STUBs (see the adapter's own KDoc) — so unlike [PaystackAdapterTest] there is no mocked
 * HTTP engine to exercise. These tests cover the demo-shaped mapping the STUB does today.
 */
class StripeAdapterTest {
    private val item = CatalogItemDto("coffee_149", "Coffee", "desc", 14_900L, "INR")
    private val adapter = StripeAdapter(publishableKey = "pk_test_fake", secret = "sk_test_fake")

    @Test
    fun `createProviderOrder returns a demo client secret and the publishable key`() =
        runTest {
            val params = adapter.createProviderOrder("order_1", item)

            assertEquals("pi_order_1_secret_demo", params["client_secret"])
            assertEquals("pk_test_fake", params["publishable_key"])
        }

    @Test
    fun `verify succeeds only when the client-supplied marker says succeeded`() =
        runTest {
            val succeeded =
                adapter.verify(
                    VerifyRequest(
                        gatewayId = "stripe",
                        orderId = "order_1",
                        extra = mapOf("payment_intent_status" to "succeeded"),
                    ),
                )
            assertEquals(PaymentStatusDto.SUCCESS, succeeded)

            val pending = adapter.verify(VerifyRequest(gatewayId = "stripe", orderId = "order_1"))
            assertEquals(PaymentStatusDto.PENDING, pending)
        }

    @Test
    fun `verify falls back to the generic marker field`() =
        runTest {
            val status =
                adapter.verify(
                    VerifyRequest(gatewayId = "stripe", orderId = "order_1", extra = mapOf("marker" to "succeeded")),
                )
            assertEquals(PaymentStatusDto.SUCCESS, status)
        }
}
