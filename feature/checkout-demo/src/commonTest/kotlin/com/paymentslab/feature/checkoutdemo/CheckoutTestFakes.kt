package com.paymentslab.feature.checkoutdemo

import com.paymentslab.core.orchestration.PaymentFlowRunner
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.CreatedOrder
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayMeta
import com.siddharth.kmp.paymentsapi.GatewayStatus
import com.siddharth.kmp.paymentsapi.PaymentGateway
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentStep
import com.siddharth.kmp.paymentsapi.PreparedPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

object TestHost : PaymentHost

/**
 * A [PaymentFlowRunner] that replays a scripted sequence of steps. [scriptFor] picks the script by
 * zero-based call index, so a test can script e.g. a failure on the first press and a success on the
 * next. Every idempotency key it's called with is captured in [keysReceived] for assertions.
 */
class FakePaymentFlowRunner(
    private val scriptFor: (callIndex: Int) -> List<PaymentStep>,
) : PaymentFlowRunner {
    constructor(script: List<PaymentStep>) : this({ script })

    var lastCatalogItemId: String? = null
        private set
    var lastGatewayId: GatewayId? = null
        private set
    val keysReceived = mutableListOf<String>()

    override fun run(
        host: PaymentHost,
        gatewayId: GatewayId,
        catalogItemId: String,
        idempotencyKey: String,
    ): Flow<PaymentStep> {
        lastCatalogItemId = catalogItemId
        lastGatewayId = gatewayId
        val callIndex = keysReceived.size
        keysReceived += idempotencyKey
        return scriptFor(callIndex).asFlow()
    }
}

/** A gateway that only carries catalog metadata — never exercised by the ViewModel. */
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

/** In-memory registry seeded with a mix of statuses so SANDBOX_READY filtering can be asserted. */
class FakeRegistry(
    override val gateways: List<PaymentGateway>,
) : PaymentGatewayRegistry {
    override fun byId(id: GatewayId): PaymentGateway? = gateways.firstOrNull { it.id == id }

    override fun withCapability(capability: Capability): List<PaymentGateway> = gateways.filter { capability in it.meta.capabilities }
}

fun sandboxAndGatedRegistry(): PaymentGatewayRegistry =
    FakeRegistry(
        listOf(
            MetaOnlyGateway(GatewayId("upi_intent"), GatewayStatus.SANDBOX_READY, "UPI Intent"),
            MetaOnlyGateway(GatewayId("razorpay"), GatewayStatus.SANDBOX_READY, "Razorpay"),
            MetaOnlyGateway(GatewayId("stripe"), GatewayStatus.KYC_GATED, "Stripe"),
            MetaOnlyGateway(GatewayId("cashfree"), GatewayStatus.COMING_SOON, "Cashfree"),
        ),
    )
