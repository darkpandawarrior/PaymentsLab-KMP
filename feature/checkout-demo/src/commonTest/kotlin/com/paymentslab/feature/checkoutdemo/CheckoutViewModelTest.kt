package com.paymentslab.feature.checkoutdemo

import com.paymentslab.core.designsystem.format
import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PaymentResult
import com.siddharth.kmp.paymentsapi.PaymentSnapshot
import com.siddharth.kmp.paymentsapi.PaymentStatus
import com.siddharth.kmp.paymentsapi.PaymentStep
import com.siddharth.kmp.paymentsapi.RedactedPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckoutViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun successScript(): List<PaymentStep> =
        listOf(
            PaymentStep.OrderCreated(
                orderId = "order_1",
                amount = Money.inr(499),
                payload = RedactedPayload.of("order", "order_id" to "order_1"),
            ),
            PaymentStep.Launching(GatewayId("razorpay")),
            PaymentStep.ClientResult(
                result = PaymentResult.Success("pay_1", mapOf("signature" to "abc"), RedactedPayload.EMPTY),
                payload = RedactedPayload.of("client", "payment_id" to "pay_1"),
            ),
            PaymentStep.Verifying(),
            PaymentStep.Settled(
                status = PaymentStatus.SUCCESS,
                snapshot = PaymentSnapshot("order_1", "pay_1", PaymentStatus.SUCCESS),
                payload = RedactedPayload.of("settled", "status" to "SUCCESS"),
            ),
        )

    private fun failScript(): List<PaymentStep> = listOf(PaymentStep.Errored(UiText.of("boom")))

    // ── (a) re-press after a failure reuses the SAME key (server dedups the retried order) ─────────
    @Test
    fun rePressingPayAfterFailure_reusesTheSameIdempotencyKey() =
        runTest {
            val runner = FakePaymentFlowRunner { _ -> failScript() }
            val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())
            vm.selectProduct(
                vm.uiState.value.products
                    .first { it.catalogItemId == "book_499" },
            )
            vm.selectGateway(GatewayId("razorpay"))

            vm.pay(TestHost) // fails
            vm.pay(TestHost) // re-press, same selection

            assertEquals(2, runner.keysReceived.size)
            assertEquals(runner.keysReceived[0], runner.keysReceived[1])
        }

    // ── (b) success then pay again → a DIFFERENT key (genuinely new order) ────────────────────────
    @Test
    fun payingAgainAfterSuccess_usesAFreshIdempotencyKey() =
        runTest {
            // Run 0 succeeds, run 1 also succeeds; keys must differ.
            val runner = FakePaymentFlowRunner { _ -> successScript() }
            val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())
            vm.selectProduct(
                vm.uiState.value.products
                    .first { it.catalogItemId == "book_499" },
            )
            vm.selectGateway(GatewayId("razorpay"))

            vm.pay(TestHost) // succeeds → key cleared
            vm.pay(TestHost) // new order

            assertEquals(2, runner.keysReceived.size)
            assertTrue(runner.keysReceived[0] != runner.keysReceived[1])
        }

    // ── (c) changing selection between presses → a DIFFERENT key ──────────────────────────────────
    @Test
    fun changingSelectionBetweenPresses_usesAFreshIdempotencyKey() =
        runTest {
            val runner = FakePaymentFlowRunner { _ -> failScript() }
            val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())
            vm.selectProduct(
                vm.uiState.value.products
                    .first { it.catalogItemId == "book_499" },
            )
            vm.selectGateway(GatewayId("razorpay"))

            vm.pay(TestHost) // fails
            vm.selectProduct(
                vm.uiState.value.products
                    .first { it.catalogItemId == "coffee_149" },
            )
            vm.pay(TestHost) // different product

            assertEquals(2, runner.keysReceived.size)
            assertTrue(runner.keysReceived[0] != runner.keysReceived[1])
        }

    @Test
    fun only_sandbox_ready_gateways_are_offered() {
        val vm = CheckoutViewModel(FakePaymentFlowRunner(emptyList()), sandboxAndGatedRegistry())
        val ids =
            vm.uiState.value.gateways
                .map { it.id.value }
        assertEquals(listOf("upi_intent", "razorpay"), ids)
    }

    @Test
    fun demo_catalog_ids_match_the_agreed_contract() {
        val vm = CheckoutViewModel(FakePaymentFlowRunner(emptyList()), sandboxAndGatedRegistry())
        assertEquals(
            listOf("coffee_149", "book_499", "headphones_2499", "course_9999", "ebook_usd_9"),
            vm.uiState.value.products
                .map { it.catalogItemId },
        )
    }

    @Test
    fun happy_path_runs_flow_and_builds_success_timeline() =
        runTest {
            val runner = FakePaymentFlowRunner(successScript())
            val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())

            val product =
                vm.uiState.value.products
                    .first { it.catalogItemId == "book_499" }
            vm.selectProduct(product)
            vm.selectGateway(GatewayId("razorpay"))
            assertTrue(vm.uiState.value.canPay)

            vm.pay(TestHost)

            val state = vm.uiState.value
            assertFalse(state.isRunning)
            assertEquals(PaymentStatus.SUCCESS, state.finalStatus)
            assertEquals(5, state.steps.size)
            assertEquals(StepState.DONE, state.steps.last().state)
            // The selected product's id was forwarded to the runner (the server resolves the price).
            assertEquals("book_499", runner.lastCatalogItemId)
            assertEquals(GatewayId("razorpay"), runner.lastGatewayId)
        }

    @Test
    fun pay_is_a_noop_without_product_and_gateway() =
        runTest {
            val runner = FakePaymentFlowRunner(successScript())
            val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())

            vm.pay(TestHost)

            assertEquals(0, vm.uiState.value.steps.size)
            assertEquals(null, runner.lastCatalogItemId)
        }

    // ── server-confirmation gating: the result page only ever reflects server-side state ─────────
    private fun selected(runner: FakePaymentFlowRunner): CheckoutViewModel {
        val vm = CheckoutViewModel(runner, sandboxAndGatedRegistry())
        vm.selectProduct(
            vm.uiState.value.products
                .first { it.catalogItemId == "book_499" },
        )
        vm.selectGateway(GatewayId("razorpay"))
        return vm
    }

    private fun CheckoutViewModel.outcome(): CheckoutOutcome? = uiState.value.result?.outcome

    private val clientSuccess =
        PaymentStep.ClientResult(
            result = PaymentResult.Success("pay_1", emptyMap(), RedactedPayload.EMPTY),
            payload = RedactedPayload.EMPTY,
        )

    private val orderCreated =
        PaymentStep.OrderCreated("order_1", Money.inr(499), RedactedPayload.EMPTY)

    @Test
    fun serverSettledSuccess_producesSuccessResultWithReceiptFields() =
        runTest {
            val vm = selected(FakePaymentFlowRunner(successScript()))

            vm.pay(TestHost)

            val result = vm.uiState.value.result
            assertEquals(CheckoutOutcome.SUCCESS, result?.outcome)
            assertEquals("order_1", result?.orderId)
            assertEquals("pay_1", result?.paymentId)
            assertEquals("Razorpay", result?.gatewayName)
            assertEquals(Money.inr(499).format(), result?.amountLabel)
            assertTrue((result?.completedAtMs ?: 0L) > 0L)
        }

    @Test
    fun clientSuccessWithoutServerSettlement_isPendingVerificationNotSuccess() =
        runTest {
            // SDK said success, the server round-trip then broke: money may have moved.
            val script =
                listOf(orderCreated, clientSuccess, PaymentStep.Verifying(), PaymentStep.Errored(UiText.of("timeout")))
            val vm = selected(FakePaymentFlowRunner(script))

            vm.pay(TestHost)

            assertEquals(CheckoutOutcome.PENDING_VERIFICATION, vm.outcome())
            assertEquals(PaymentStatus.PENDING, vm.uiState.value.finalStatus)
        }

    @Test
    fun streamEndingBeforeSettled_afterClientSuccess_isPendingVerification() =
        runTest {
            val vm = selected(FakePaymentFlowRunner(listOf(orderCreated, clientSuccess, PaymentStep.Verifying())))

            vm.pay(TestHost)

            assertEquals(CheckoutOutcome.PENDING_VERIFICATION, vm.outcome())
        }

    @Test
    fun serverSettledPending_isPendingVerification() =
        runTest {
            val script =
                listOf(
                    orderCreated,
                    clientSuccess,
                    PaymentStep.Settled(
                        PaymentStatus.PENDING,
                        PaymentSnapshot("order_1", "pay_1", PaymentStatus.PENDING),
                        RedactedPayload.EMPTY,
                    ),
                )
            val vm = selected(FakePaymentFlowRunner(script))

            vm.pay(TestHost)

            assertEquals(CheckoutOutcome.PENDING_VERIFICATION, vm.outcome())
        }

    @Test
    fun erroredWithoutAnyClientCharge_isFailureWithReason() =
        runTest {
            val vm = selected(FakePaymentFlowRunner(failScript()))

            vm.pay(TestHost)

            val result = vm.uiState.value.result
            assertEquals(CheckoutOutcome.FAILURE, result?.outcome)
            assertEquals("boom", result?.reason)
            assertEquals(PaymentStatus.FAILED, vm.uiState.value.finalStatus)
        }

    @Test
    fun serverSettledFailed_isFailure() =
        runTest {
            val script =
                listOf(
                    orderCreated,
                    PaymentStep.Settled(
                        PaymentStatus.FAILED,
                        PaymentSnapshot("order_1", null, PaymentStatus.FAILED),
                        RedactedPayload.EMPTY,
                    ),
                )
            val vm = selected(FakePaymentFlowRunner(script))

            vm.pay(TestHost)

            assertEquals(CheckoutOutcome.FAILURE, vm.outcome())
        }

    @Test
    fun noResultBeforeAnyRun_andRetryAfterFailureReplacesIt() =
        runTest {
            val runner = FakePaymentFlowRunner { i -> if (i == 0) failScript() else successScript() }
            val vm = selected(runner)
            assertEquals(null, vm.uiState.value.result)

            vm.pay(TestHost)
            assertEquals(CheckoutOutcome.FAILURE, vm.outcome())

            vm.pay(TestHost)
            assertEquals(CheckoutOutcome.SUCCESS, vm.outcome())
        }

    @Test
    fun dismissResult_clearsResultButKeepsSelection() =
        runTest {
            val vm = selected(FakePaymentFlowRunner(failScript()))
            vm.pay(TestHost)

            vm.dismissResult()

            val state = vm.uiState.value
            assertEquals(null, state.result)
            assertTrue(state.steps.isEmpty())
            assertEquals("book_499", state.selectedProduct?.catalogItemId)
            assertEquals(GatewayId("razorpay"), state.selectedGatewayId)
        }

    @Test
    fun formatUtc_rendersEpochAsUtcMinutes() {
        assertEquals("1970-01-01 00:00", formatUtc(0L))
        assertEquals("2025-10-09 08:53", formatUtc(1_760_000_000_000L))
    }
}
