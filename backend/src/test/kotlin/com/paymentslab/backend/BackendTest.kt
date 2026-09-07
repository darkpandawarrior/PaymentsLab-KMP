package com.paymentslab.backend

import com.paymentslab.core.protocol.CreateOrderRequest
import com.paymentslab.core.protocol.OrderResponse
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.PaymentStatusResponse
import com.paymentslab.core.protocol.VerifyRequest
import com.paymentslab.core.protocol.VerifyResponse
import com.paymentslab.core.protocol.WebhookAck
import com.siddharth.kmp.paymentsapi.WalletBalanceResponse
import com.siddharth.kmp.paymentsapi.WalletDebitRequest
import com.siddharth.kmp.paymentsapi.WalletTransactionResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests over the real routes via [testApplication] (no bound port). Deterministic + fast.
 */
class BackendTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // Same test defaults the module boots with — used to recompute the expected Razorpay HMAC.
    private val razorpaySecret = "test_razorpay_secret"
    private val webhookSecret = "test_razorpay_webhook_secret"

    private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

    // ── Test 1: server price is authoritative; the coffee item is ₹149.00 = 14900 minor ─────────
    @Test
    fun `order creation uses server price ignoring any client amount`() =
        testApplication {
            application { module() }

            // The wire DTO has no amount field, so a client literally cannot send one. We assert the
            // server resolves the catalog price regardless — the trust boundary in action.
            val resp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            CreateOrderRequest(
                                catalogItemId = "coffee_149",
                                gatewayId = "razorpay",
                                idempotencyKey = "idem_1",
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            val order = decode<OrderResponse>(resp.bodyAsText())
            assertEquals(14_900L, order.amountMinor)
            assertEquals("INR", order.currency)
            assertEquals("coffee_149", order.catalogItemId)
            // Provider params carry the amount from the server, not the client.
            assertEquals("14900", order.providerParams["amount"])
            assertTrue(order.providerParams["key_id"]!!.startsWith("rzp_test_"))
        }

    // ── Test 1b: Flutterwave now has a registered adapter — `POST /orders` no longer 400s ─────────
    @Test
    fun `flutterwave order creation succeeds and returns a checkout_url`() =
        testApplication {
            application { module() }
            val resp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            CreateOrderRequest(
                                catalogItemId = "coffee_149",
                                gatewayId = "flutterwave",
                                idempotencyKey = "idem_flutterwave_1",
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            val order = decode<OrderResponse>(resp.bodyAsText())
            assertEquals("flutterwave", order.gatewayId)
            assertTrue(
                order.providerParams["checkout_url"]!!.contains("/mock/checkout/flutterwave"),
            )
        }

    // ── Test 1c: Stripe order creation + STUB verify end-to-end through the real routes ───────────
    @Test
    fun `stripe order creation returns a client secret and verify honors the stub marker`() =
        testApplication {
            application { module() }
            val createResp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            CreateOrderRequest(
                                catalogItemId = "coffee_149",
                                gatewayId = "stripe",
                                idempotencyKey = "idem_stripe_1",
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, createResp.status)
            val order = decode<OrderResponse>(createResp.bodyAsText())
            assertTrue(order.providerParams["client_secret"]!!.startsWith("pi_"))
            assertTrue(order.providerParams.containsKey("publishable_key"))

            val verifyResp =
                client.post("/payments/${order.orderId}/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            VerifyRequest(
                                gatewayId = "stripe",
                                orderId = order.orderId,
                                extra = mapOf("marker" to "succeeded"),
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, verifyResp.status)
            assertEquals(PaymentStatusDto.SUCCESS, decode<VerifyResponse>(verifyResp.bodyAsText()).status)
        }

    // ── Test 2: Razorpay verify — real HMAC SUCCESS vs wrong-signature FAILED ────────────────────
    @Test
    fun `razorpay verify succeeds with correct HMAC and fails with wrong signature`() =
        testApplication {
            application { module() }

            val orderResp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(CreateOrderRequest("book_499", "razorpay", "idem_2")))
                }
            val orderId = decode<OrderResponse>(orderResp.bodyAsText()).orderId
            val paymentId = "pay_test_123"

            // Compute the expected signature the SAME way the server does: HMAC-SHA256("orderId|paymentId").
            val goodSig = Crypto.hmacSha256Hex(razorpaySecret, "$orderId|$paymentId")

            val okResp =
                client.post("/payments/$orderId/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(VerifyRequest("razorpay", orderId, paymentId, goodSig)))
                }
            assertEquals(HttpStatusCode.OK, okResp.status)
            assertEquals(PaymentStatusDto.SUCCESS, decode<VerifyResponse>(okResp.bodyAsText()).status)

            val badResp =
                client.post("/payments/$orderId/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(VerifyRequest("razorpay", orderId, paymentId, "deadbeef")))
                }
            assertEquals(PaymentStatusDto.FAILED, decode<VerifyResponse>(badResp.bodyAsText()).status)
        }

    // ── Test 3: webhook idempotency — same eventId twice → duplicate + state unchanged ──────────
    @Test
    fun `webhook is idempotent on eventId`() =
        testApplication {
            application { module() }

            val orderId =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("headphones_2499", "upi_intent", "idem_3")))
                        }.bodyAsText(),
                ).orderId

            val eventBody = """{"eventId":"evt_1","orderId":"$orderId","status":"success","paymentId":"pay_777"}"""
            val sig = Crypto.hmacSha256Hex(webhookSecret, eventBody)

            val first =
                client.post("/webhooks/razorpay") {
                    contentType(ContentType.Application.Json)
                    header("X-Razorpay-Signature", sig)
                    setBody(eventBody)
                }
            val firstAck = decode<WebhookAck>(first.bodyAsText())
            assertTrue(firstAck.received)
            assertEquals(false, firstAck.duplicate)

            // Status advanced to success after the first webhook.
            val afterFirst = decode<PaymentStatusResponse>(client.get("/payments/$orderId").bodyAsText())
            assertEquals(PaymentStatusDto.SUCCESS, afterFirst.status)

            // Redeliver the SAME event id.
            val second =
                client.post("/webhooks/razorpay") {
                    contentType(ContentType.Application.Json)
                    header("X-Razorpay-Signature", sig)
                    setBody(eventBody)
                }
            val secondAck = decode<WebhookAck>(second.bodyAsText())
            assertTrue("second delivery of same eventId must be duplicate", secondAck.duplicate)

            val afterSecond = decode<PaymentStatusResponse>(client.get("/payments/$orderId").bodyAsText())
            // State unchanged (same status + same updatedAt timestamp) — the duplicate was a no-op.
            assertEquals(PaymentStatusDto.SUCCESS, afterSecond.status)
            assertEquals(afterFirst.updatedAtEpochMs, afterSecond.updatedAtEpochMs)
            assertEquals(afterFirst.paymentId, afterSecond.paymentId)
        }

    // ── Test 4: unknown catalog item → 400 ──────────────────────────────────────────────────────
    @Test
    fun `unknown catalog item returns 400`() =
        testApplication {
            application { module() }

            val resp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(CreateOrderRequest("does_not_exist", "razorpay", "idem_4")))
                }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertTrue(resp.bodyAsText().contains("unknown_catalog_item"))
        }

    // ── Bonus: unknown gateway → 400 ────────────────────────────────────────────────────────────
    @Test
    fun `unknown gateway returns 400`() =
        testApplication {
            application { module() }
            val resp =
                client.post("/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(CreateOrderRequest("coffee_149", "no_such_gateway", "idem_5")))
                }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertTrue(resp.bodyAsText().contains("unknown_gateway"))
        }

    // ── Test 5: webhook signature verification is now dispatched through GatewayAdapter ─────────
    @Test
    fun `webhook with wrong razorpay signature is rejected via the adapter`() =
        testApplication {
            application { module() }
            val orderId =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "razorpay", "idem_6")))
                        }.bodyAsText(),
                ).orderId
            val body = """{"eventId":"evt_bad","orderId":"$orderId","status":"success"}"""

            val resp =
                client.post("/webhooks/razorpay") {
                    contentType(ContentType.Application.Json)
                    header("X-Razorpay-Signature", "not_the_real_signature")
                    setBody(body)
                }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
            assertTrue(resp.bodyAsText().contains("bad_signature"))
        }

    // ── Test 6: webhook for an unknown gateway → 400 (adapter lookup happens before body decode) ─
    @Test
    fun `webhook for an unknown gateway returns 400`() =
        testApplication {
            application { module() }
            val resp =
                client.post("/webhooks/no_such_gateway") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"eventId":"e","orderId":"o","status":"success"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertTrue(resp.bodyAsText().contains("unknown_gateway"))
        }

    // ── Test 7: the generic mock-checkout page renders Pay/Fail links for any provider ────────────
    @Test
    fun `mock checkout page serves html with pay and fail links`() =
        testApplication {
            application { module() }
            val resp = client.get("/mock/checkout/paystack?orderId=order_abc")
            assertEquals(HttpStatusCode.OK, resp.status)
            val html = resp.bodyAsText()
            assertTrue(html.contains("/mock/return/success?payment_id=mock_pay_order_abc"))
            assertTrue(html.contains("/mock/return/failure"))
        }

    // ── Test 8: mock momo flip resolves the order after its delay, via the PaymentActor ──────────
    @Test
    fun `mock momo flip resolves the order to success after its delay`() =
        testApplication {
            application { module() }
            val orderId =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "razorpay", "idem_7")))
                        }.bodyAsText(),
                ).orderId

            val scheduleResp = client.post("/mock/momo/mtn_momo?orderId=$orderId&delayMs=10")
            assertEquals(HttpStatusCode.OK, scheduleResp.status)

            // The flip runs on the application's own coroutine scope; give it a moment to fire.
            delay(200)

            val status = decode<PaymentStatusResponse>(client.get("/payments/$orderId").bodyAsText())
            assertEquals(PaymentStatusDto.SUCCESS, status.status)
            assertEquals("momo_pay_$orderId", status.paymentId)
        }

    // ── Test 8b: mock cash settle flips the order to SUCCESS, and is idempotent on repeat ─────────
    @Test
    fun `mock cash settle resolves the order and is idempotent`() =
        testApplication {
            application { module() }
            val orderId =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "cash", "idem_cash")))
                        }.bodyAsText(),
                ).orderId

            val firstSettle = client.post("/mock/cash/$orderId/settle")
            assertEquals(HttpStatusCode.OK, firstSettle.status)
            assertEquals("false", decode<Map<String, String>>(firstSettle.bodyAsText())["duplicate"])

            val status = decode<PaymentStatusResponse>(client.get("/payments/$orderId").bodyAsText())
            assertEquals(PaymentStatusDto.SUCCESS, status.status)
            assertEquals("cash_pay_$orderId", status.paymentId)

            // Settling again (double-click / retry) must not error and must report duplicate=true.
            val secondSettle = client.post("/mock/cash/$orderId/settle")
            assertEquals(HttpStatusCode.OK, secondSettle.status)
            assertEquals("true", decode<Map<String, String>>(secondSettle.bodyAsText())["duplicate"])
        }

    // ── Test 8c: mock Xendit/M-Pesa webhook settle flips the order to SUCCESS, idempotently ───────
    @Test
    fun `mock xendit settle resolves the order and is idempotent`() = mockWebhookSettleResolvesOrder("xendit")

    @Test
    fun `mock mpesa settle resolves the order and is idempotent`() = mockWebhookSettleResolvesOrder("mpesa")

    private fun mockWebhookSettleResolvesOrder(provider: String) =
        testApplication {
            application { module() }
            val orderId =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", provider, "idem_$provider")))
                        }.bodyAsText(),
                ).orderId

            val firstSettle = client.post("/mock/$provider/$orderId/settle")
            assertEquals(HttpStatusCode.OK, firstSettle.status)
            assertEquals("false", decode<Map<String, String>>(firstSettle.bodyAsText())["duplicate"])

            val status = decode<PaymentStatusResponse>(client.get("/payments/$orderId").bodyAsText())
            assertEquals(PaymentStatusDto.SUCCESS, status.status)
            assertEquals("${provider}_pay_$orderId", status.paymentId)

            // Settling again (double-click / retried webhook) must not error and must report duplicate=true.
            val secondSettle = client.post("/mock/$provider/$orderId/settle")
            assertEquals(HttpStatusCode.OK, secondSettle.status)
            assertEquals("true", decode<Map<String, String>>(secondSettle.bodyAsText())["duplicate"])
        }

    // ── Test 9: idempotencyKey dedup — same key twice → one order, same orderId ──────────────────
    @Test
    fun `createOrder with the same idempotencyKey twice returns the same order`() =
        testApplication {
            application { module() }

            suspend fun createOrder() =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "razorpay", "idem_dedup")))
                        }.bodyAsText(),
                )

            val first = createOrder()
            val second = createOrder()

            assertEquals(first.orderId, second.orderId)
            assertEquals(first.amountMinor, second.amountMinor)
            assertEquals(first.providerParams, second.providerParams)
        }

    // ── Test 10: concurrent createOrder with the same idempotencyKey → single order ────────────────
    @Test
    fun `concurrent createOrder calls with the same idempotencyKey yield a single order`() =
        testApplication {
            application { module() }

            suspend fun createOrder() =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "razorpay", "idem_race")))
                        }.bodyAsText(),
                )

            val results =
                coroutineScope {
                    (1..8)
                        .map { async { createOrder() } }
                        .map { it.await() }
                }

            val distinctOrderIds = results.map { it.orderId }.distinct()
            assertEquals("expected exactly one orderId, saw $distinctOrderIds", 1, distinctOrderIds.size)
        }

    // ── Test 11: different idempotencyKeys → different orders ──────────────────────────────────────
    @Test
    fun `createOrder with different idempotencyKeys creates different orders`() =
        testApplication {
            application { module() }

            suspend fun createOrder(idempotencyKey: String) =
                decode<OrderResponse>(
                    client
                        .post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(CreateOrderRequest("coffee_149", "razorpay", idempotencyKey)))
                        }.bodyAsText(),
                )

            val first = createOrder("idem_a")
            val second = createOrder("idem_b")

            assertTrue(first.orderId != second.orderId)
        }

    // ── Test 12: wallet ledger routes — seed, balance, debit, insufficient funds ───────────────────
    @Test
    fun `wallet debit route moves balance and rejects overdraft`() =
        testApplication {
            application { module() }
            val accountId = "wallet_route_user"

            client.post("/wallet/$accountId/seed?amountMinor=5000")
            val seeded = decode<WalletBalanceResponse>(client.get("/wallet/$accountId/balance").bodyAsText())
            assertEquals(5000, seeded.balanceMinor)

            val debitResp =
                client.post("/wallet/$accountId/debit") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(WalletDebitRequest("wallet_route_key", 2000)))
                }
            assertEquals(HttpStatusCode.OK, debitResp.status)
            val txn = decode<WalletTransactionResponse>(debitResp.bodyAsText())
            assertEquals(3000, txn.balanceMinor)

            val overdraft =
                client.post("/wallet/$accountId/debit") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(WalletDebitRequest("wallet_route_overdraft", 9000)))
                }
            assertEquals(HttpStatusCode.BadRequest, overdraft.status)
        }
}
