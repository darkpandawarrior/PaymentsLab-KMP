package com.paymentslab.feature.lab.explain

import com.siddharth.kmp.paymentsapi.GatewayId
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
import kotlin.test.assertNull

/**
 * Covers what [FlowDiffPanel] actually renders: [FlowDiffViewModel.uiState].diff only appears once
 * two DIFFERENT recorded gateways are picked, and reflects [RecordedFlowStore] updates live.
 * [FlowDiffExplainer.streamNarrative] itself (the panel's model tier) is already covered end to end
 * by FlowDiffTest — same accumulation/failure-path assertions [ExplainerPanel] relies on for its
 * sibling feature.
 */
class FlowDiffViewModelTest {
    private val razorpay = GatewayId("razorpay")
    private val stripe = GatewayId("stripe")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settledTrace(status: PaymentStatus): List<PaymentStep> =
        listOf(
            PaymentStep.Settled(
                status = status,
                snapshot = PaymentSnapshot("order_1", "pay_1", status),
                payload = RedactedPayload.of("settled", "status" to status.name),
            ),
        )

    @Test
    fun noDiff_untilTwoDifferentGatewaysAreRecordedAndPicked() =
        runTest {
            val store = RecordedFlowStore()
            val vm = FlowDiffViewModel(store)

            assertNull(vm.uiState.value.diff)

            store.record(razorpay, settledTrace(PaymentStatus.SUCCESS))
            vm.selectBaseline(razorpay)
            assertNull(vm.uiState.value.diff) // only one side picked

            vm.selectCompared(razorpay)
            assertNull(vm.uiState.value.diff) // same gateway both sides — not a comparison
        }

    @Test
    fun picking_two_recorded_gateways_produces_the_deterministic_diff() =
        runTest {
            val store = RecordedFlowStore()
            store.record(razorpay, settledTrace(PaymentStatus.SUCCESS))
            store.record(stripe, settledTrace(PaymentStatus.FAILED))
            val vm = FlowDiffViewModel(store)

            vm.selectBaseline(razorpay)
            vm.selectCompared(stripe)

            val diff = vm.uiState.value.diff
            assertEquals(true, diff?.hasDivergence)
            assertEquals(
                listOf(razorpay, stripe),
                listOf(diff?.baseline?.gatewayId, diff?.compared?.gatewayId),
            )
        }

    @Test
    fun a_run_recorded_after_selection_updates_the_diff_live() =
        runTest {
            val store = RecordedFlowStore()
            store.record(razorpay, settledTrace(PaymentStatus.SUCCESS))
            store.record(stripe, settledTrace(PaymentStatus.SUCCESS))
            val vm = FlowDiffViewModel(store)
            vm.selectBaseline(razorpay)
            vm.selectCompared(stripe)

            assertEquals(
                false,
                vm.uiState.value.diff
                    ?.hasDivergence,
            )

            // Stripe re-run now fails — the same instances stay selected, the diff picks it up.
            store.record(stripe, settledTrace(PaymentStatus.FAILED))

            assertEquals(
                true,
                vm.uiState.value.diff
                    ?.hasDivergence,
            )
        }
}
