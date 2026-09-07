package com.paymentslab.feature.lab

import com.paymentslab.core.designsystem.FlowHop
import com.paymentslab.core.orchestration.fsm.PaymentPhase
import com.siddharth.kmp.common.UiText
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.paymentsapi.FailureCode
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

class ProviderLabViewModelTest {
    private val gatewayId = GatewayId("razorpay")

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
            PaymentStep.Launching(gatewayId),
            PaymentStep.ClientResult(
                result =
                    PaymentResult.Success(
                        paymentId = "pay_1",
                        verification = mapOf("signature" to "abc"),
                        raw = RedactedPayload.of("client", "payment_id" to "pay_1"),
                    ),
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

    private fun gatewayDeclineScript(): List<PaymentStep> =
        listOf(
            PaymentStep.OrderCreated(
                orderId = "order_1",
                amount = Money.inr(499),
                payload = RedactedPayload.of("order", "order_id" to "order_1"),
            ),
            PaymentStep.Launching(gatewayId),
            PaymentStep.ClientResult(
                result =
                    PaymentResult.Failure(
                        code = FailureCode.GATEWAY_DECLINED,
                        message = UiText.of("Card declined"),
                        raw = RedactedPayload.of("razorpay_result", "code" to "2", "description" to "Card declined"),
                    ),
                payload = RedactedPayload.of("razorpay_result", "code" to "2", "description" to "Card declined"),
            ),
            PaymentStep.Verifying(),
            PaymentStep.Settled(
                status = PaymentStatus.FAILED,
                snapshot = PaymentSnapshot("order_1", null, PaymentStatus.FAILED),
                payload = RedactedPayload.of("settled", "status" to "FAILED"),
            ),
        )

    // ── the FSM-grounded gateway failure feeding ErrorExplainer/ExplainerPanel ──────────────────────
    @Test
    fun serverConfirmedDecline_exposesGatewayFailure_groundedAtTerminalPhase() =
        runTest {
            val runner = FakePaymentFlowRunner(gatewayDeclineScript())
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book")

            val failure = vm.uiState.value.gatewayFailure
            assertEquals(gatewayId, failure?.gatewayId)
            assertEquals(FailureCode.GATEWAY_DECLINED, failure?.code)
            assertEquals(
                "Card declined",
                failure
                    ?.payload
                    ?.entries
                    ?.toMap()
                    ?.get("description"),
            )
            // Server actually settled FAILED — the client's decline is now confirmed, not just a hint.
            assertEquals(PaymentPhase.TERMINAL, failure?.phase)
        }

    @Test
    fun successfulRun_exposesNoGatewayFailure() =
        runTest {
            val runner = FakePaymentFlowRunner(successScript())
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book")

            assertEquals(null, vm.uiState.value.gatewayFailure)
        }

    // ── "Run again" after a failure reuses the SAME key (server dedups the retried order) ──────────
    @Test
    fun runAgainAfterFailure_reusesTheSameIdempotencyKey() =
        runTest {
            val runner = FakePaymentFlowRunner { _ -> failScript() }
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book") // fails
            vm.start(TestHost, gatewayId, "book") // Run again, same attempt

            assertEquals(2, runner.keysReceived.size)
            assertEquals(runner.keysReceived[0], runner.keysReceived[1])
        }

    // ── after a terminal SUCCESS, the next run is a genuinely new order → fresh key ────────────────
    @Test
    fun runningAgainAfterSuccess_usesAFreshIdempotencyKey() =
        runTest {
            val runner = FakePaymentFlowRunner { _ -> successScript() }
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book") // succeeds → key cleared
            vm.start(TestHost, gatewayId, "book") // new order

            assertEquals(2, runner.keysReceived.size)
            assertTrue(runner.keysReceived[0] != runner.keysReceived[1])
        }

    @Test
    fun maps_scripted_success_flow_to_ordered_timeline() =
        runTest {
            val runner = FakePaymentFlowRunner(successScript())
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book")

            val state = vm.uiState.value
            assertFalse(state.isRunning)
            assertEquals(PaymentStatus.SUCCESS, state.finalStatus)
            assertEquals(5, state.steps.size)

            assertEquals("Order created", state.steps[0].title)
            assertEquals("Launching razorpay", state.steps[1].title)
            assertEquals("Client result", state.steps[2].title)
            assertEquals("Verifying", state.steps[3].title)
            assertEquals("Settled", state.steps[4].title)

            // Every non-terminal step is DONE; the terminal SUCCESS step is DONE.
            assertTrue(state.steps.take(4).all { it.state == StepState.DONE })
            assertEquals(StepState.DONE, state.steps[4].state)
            // Order payload rows are carried through the mapping.
            assertEquals(listOf("order_id" to "order_1"), state.steps[0].payload)
            // The last emitted step (Settled) lands on the backend hop, backend-confirmed.
            assertEquals(FlowHop.BACKEND, state.currentHop)
            assertTrue(state.verified)
        }

    @Test
    fun settled_failure_marks_final_step_error() =
        runTest {
            val script =
                listOf(
                    PaymentStep.OrderCreated(
                        orderId = "order_2",
                        amount = Money.inr(149),
                        payload = RedactedPayload.EMPTY,
                    ),
                    PaymentStep.Settled(
                        status = PaymentStatus.FAILED,
                        snapshot = PaymentSnapshot("order_2", null, PaymentStatus.FAILED),
                        payload = RedactedPayload.EMPTY,
                    ),
                )
            val vm = ProviderLabViewModel(FakePaymentFlowRunner(script))

            vm.start(TestHost, gatewayId, "coffee")

            val state = vm.uiState.value
            assertEquals(PaymentStatus.FAILED, state.finalStatus)
            assertEquals(StepState.ERROR, state.steps.last().state)
        }

    @Test
    fun errored_step_maps_to_error_and_failed_status() =
        runTest {
            val script =
                listOf(
                    PaymentStep.Errored(message = UiText.Dynamic("network down")),
                )
            val vm = ProviderLabViewModel(FakePaymentFlowRunner(script))

            vm.start(TestHost, gatewayId, "coffee")

            val state = vm.uiState.value
            assertEquals(1, state.steps.size)
            assertEquals(StepState.ERROR, state.steps[0].state)
            assertEquals("network down", state.steps[0].subtitle)
            assertEquals(PaymentStatus.FAILED, state.finalStatus)
            // The flow broke before reaching anywhere authoritative — stays at APP, unverified.
            assertEquals(FlowHop.APP, state.currentHop)
            assertFalse(state.verified)
        }

    @Test
    fun run_again_resets_and_reruns() =
        runTest {
            val runner = FakePaymentFlowRunner(successScript())
            val vm = ProviderLabViewModel(runner)

            vm.start(TestHost, gatewayId, "book")
            assertTrue(vm.uiState.value.hasRun)
            vm.start(TestHost, gatewayId, "book")

            assertEquals(2, runner.runCount)
            assertEquals(5, vm.uiState.value.steps.size)
        }
}
