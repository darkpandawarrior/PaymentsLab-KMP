package com.paymentslab.feature.home

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PendingPayment
import com.siddharth.kmp.paymentsapi.PendingPaymentJournal
import com.siddharth.kmp.paymentsapi.PreparedPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private class MetaOnlyGateway(
    override val id: GatewayId,
    status: GatewayStatus,
    name: String,
) : PaymentGateway {
    override val meta =
        GatewayMeta(
            displayName = name,
            status = status,
            capabilities = setOf(Capability.ONE_TIME_PAYMENT),
            region = "IN",
            docsPath = "docs/${id.value}.md",
            blurb = "fake",
        )

    override suspend fun prepare(created: CreatedOrder): PreparedPayment = error("unused")

    override suspend fun pay(
        host: PaymentHost,
        prepared: PreparedPayment,
    ): PaymentResult = error("unused")
}

class FakeGatewayRegistry(
    override val gateways: List<PaymentGateway>,
) : PaymentGatewayRegistry {
    override fun byId(id: GatewayId): PaymentGateway? = gateways.firstOrNull { it.id == id }

    override fun withCapability(capability: Capability): List<PaymentGateway> = gateways.filter { capability in it.meta.capabilities }
}

fun mixedStatusRegistry(): PaymentGatewayRegistry =
    FakeGatewayRegistry(
        listOf(
            MetaOnlyGateway(GatewayId("upi_intent"), GatewayStatus.SANDBOX_READY, "UPI Intent"),
            MetaOnlyGateway(GatewayId("razorpay"), GatewayStatus.SANDBOX_READY, "Razorpay"),
            MetaOnlyGateway(GatewayId("mollie"), GatewayStatus.MOCK_MODE, "Mollie"),
            MetaOnlyGateway(GatewayId("stripe"), GatewayStatus.KYC_GATED, "Stripe"),
            MetaOnlyGateway(GatewayId("some_future_gateway"), GatewayStatus.COMING_SOON, "Future Gateway"),
        ),
    )

class FakePendingPaymentJournal(
    initial: List<PendingPayment> = emptyList(),
) : PendingPaymentJournal {
    private val state = MutableStateFlow(initial)

    fun emit(payments: List<PendingPayment>) {
        state.value = payments
    }

    override suspend fun record(entry: PendingPayment) {
        state.update { it + entry }
    }

    override suspend fun markResolved(
        orderId: String,
        status: PaymentStatus,
        paymentId: String?,
    ) {
        state.update { list ->
            list.map { if (it.orderId == orderId) it.copy(status = status, paymentId = paymentId) else it }
        }
    }

    override suspend fun unresolved(): List<PendingPayment> = state.value.filter { !it.status.isTerminal }

    override fun observeAll(): Flow<List<PendingPayment>> = state
}
