package com.paymentslab.core.network

import com.paymentslab.core.protocol.OrderResponse
import com.paymentslab.core.protocol.PaymentStatusDto
import com.paymentslab.core.protocol.PaymentStatusResponse
import com.paymentslab.core.protocol.PayoutResponse
import com.paymentslab.core.protocol.PayoutStatusDto
import com.paymentslab.core.protocol.VerifyResponse
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PayoutStatus
import com.siddharth.kmp.paymentsapi.VerificationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DtoMappersTest {
    @Test
    fun statusDto_roundTrips_domainToDtoToDomain_forEveryValue() {
        for (status in PaymentStatus.entries) {
            assertEquals(status, status.toDto().toDomain(), "domain->dto->domain lost $status")
        }
    }

    @Test
    fun statusDto_roundTrips_dtoToDomainToDto_forEveryValue() {
        for (dto in PaymentStatusDto.entries) {
            assertEquals(dto, dto.toDomain().toDto(), "dto->domain->dto lost $dto")
        }
    }

    @Test
    fun statusDto_mapsEachValueExplicitly() {
        assertEquals(PaymentStatus.CREATED, PaymentStatusDto.CREATED.toDomain())
        assertEquals(PaymentStatus.PENDING, PaymentStatusDto.PENDING.toDomain())
        assertEquals(PaymentStatus.SUCCESS, PaymentStatusDto.SUCCESS.toDomain())
        assertEquals(PaymentStatus.FAILED, PaymentStatusDto.FAILED.toDomain())
        assertEquals(PaymentStatus.CANCELLED, PaymentStatusDto.CANCELLED.toDomain())
        assertEquals(PaymentStatus.REFUNDED, PaymentStatusDto.REFUNDED.toDomain())
    }

    @Test
    fun createOrderRequest_carriesGatewayValue_andNoAmount() {
        val req = createOrderRequest("catalog-42", GatewayId("razorpay"), "idem-1")
        assertEquals("catalog-42", req.catalogItemId)
        assertEquals("razorpay", req.gatewayId)
        assertEquals("idem-1", req.idempotencyKey)
    }

    @Test
    fun orderResponse_mapsToCreatedOrder_withMoneyAndParams() {
        val response =
            OrderResponse(
                orderId = "order_1",
                catalogItemId = "catalog-42",
                amountMinor = 49900,
                currency = "INR",
                gatewayId = "razorpay",
                providerParams = mapOf("key_id" to "rzp_test", "order_id" to "order_1"),
            )

        val created = response.toDomain()

        assertEquals("order_1", created.order.orderId)
        assertEquals("catalog-42", created.order.catalogItemId)
        assertEquals(49900, created.order.amount.amountMinor)
        assertEquals("INR", created.order.amount.currency)
        assertEquals(GatewayId("razorpay"), created.gatewayId)
        assertEquals("rzp_test", created.providerParams["key_id"])
    }

    @Test
    fun verificationRequest_mapsToVerifyDto_preservingProof() {
        val domain =
            VerificationRequest(
                gatewayId = GatewayId("razorpay"),
                orderId = "order_1",
                paymentId = "pay_9",
                signature = "sig_abc",
                extra = mapOf("k" to "v"),
            )

        val dto = domain.toDto()

        assertEquals("razorpay", dto.gatewayId)
        assertEquals("order_1", dto.orderId)
        assertEquals("pay_9", dto.paymentId)
        assertEquals("sig_abc", dto.signature)
        assertEquals("v", dto.extra["k"])
    }

    @Test
    fun verifyResponse_mapsToSnapshot_threadingOrderId() {
        val response = VerifyResponse(status = PaymentStatusDto.SUCCESS, paymentId = "pay_9", message = "ok")

        val snapshot = response.toSnapshot(orderId = "order_1")

        assertEquals("order_1", snapshot.orderId)
        assertEquals("pay_9", snapshot.paymentId)
        assertEquals(PaymentStatus.SUCCESS, snapshot.status)
        assertNull(snapshot.providerRef)
    }

    @Test
    fun statusResponse_mapsToSnapshot_withProviderRef() {
        val response =
            PaymentStatusResponse(
                orderId = "order_1",
                paymentId = "pay_9",
                status = PaymentStatusDto.PENDING,
                updatedAtEpochMs = 1_700_000_000_000,
                providerRef = "ref_xyz",
            )

        val snapshot = response.toSnapshot()

        assertEquals("order_1", snapshot.orderId)
        assertEquals("pay_9", snapshot.paymentId)
        assertEquals(PaymentStatus.PENDING, snapshot.status)
        assertEquals("ref_xyz", snapshot.providerRef)
    }

    @Test
    fun payoutStatusDto_mapsEachValueExplicitly() {
        assertEquals(PayoutStatus.PENDING, PayoutStatusDto.PENDING.toDomain())
        assertEquals(PayoutStatus.SETTLED, PayoutStatusDto.SETTLED.toDomain())
        assertEquals(PayoutStatus.FAILED, PayoutStatusDto.FAILED.toDomain())
    }

    @Test
    fun payoutResponse_mapsToSnapshot() {
        val response =
            PayoutResponse(
                payoutId = "payout_1",
                gatewayId = "paystack",
                recipientRef = "recipient_1",
                amountMinor = 5_000L,
                currency = "NGN",
                status = PayoutStatusDto.PENDING,
                updatedAtEpochMs = 1_700_000_000_000,
            )

        val snapshot = response.toSnapshot()

        assertEquals("payout_1", snapshot.payoutId)
        assertEquals(GatewayId("paystack"), snapshot.gatewayId)
        assertEquals("recipient_1", snapshot.recipientRef)
        assertEquals(5_000L, snapshot.amount.amountMinor)
        assertEquals("NGN", snapshot.amount.currency)
        assertEquals(PayoutStatus.PENDING, snapshot.status)
    }
}
