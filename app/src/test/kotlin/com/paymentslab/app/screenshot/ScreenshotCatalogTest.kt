package com.paymentslab.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.paymentslab.core.designsystem.AnimatedAmount
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.FailureShake
import com.paymentslab.core.designsystem.FlowHop
import com.paymentslab.core.designsystem.GatewayBrandAsset
import com.paymentslab.core.designsystem.GatewayBranding
import com.paymentslab.core.designsystem.GatewayStatusBadge
import com.paymentslab.core.designsystem.GatewayStatusUi
import com.paymentslab.core.designsystem.LocalReducedMotion
import com.paymentslab.core.designsystem.PaymentFlowDiagram
import com.paymentslab.core.designsystem.PaymentsLabTheme
import com.paymentslab.core.designsystem.PrimaryButton
import com.paymentslab.core.designsystem.SectionHeader
import com.paymentslab.core.designsystem.ShieldPulse
import com.paymentslab.core.designsystem.SuccessBurst
import com.paymentslab.feature.checkoutdemo.CheckoutGateway
import com.paymentslab.feature.checkoutdemo.CheckoutScreen
import com.paymentslab.feature.checkoutdemo.CheckoutUiState
import com.paymentslab.feature.checkoutdemo.DEMO_PRODUCTS
import com.paymentslab.feature.history.HistoryRow
import com.paymentslab.feature.history.HistoryScreen
import com.paymentslab.feature.history.HistoryUiState
import com.paymentslab.feature.home.HomeScreen
import com.paymentslab.feature.home.HomeUiState
import com.paymentslab.feature.home.RecentActivityRow
import com.paymentslab.feature.lab.LabHomeScreen
import com.paymentslab.feature.lab.LabHomeUiState
import com.paymentslab.feature.lab.ProviderLabScreen
import com.paymentslab.feature.lab.ProviderLabUiState
import com.paymentslab.feature.lab.ProviderRow
import com.siddharth.kmp.designsystem.PayloadCard
import com.siddharth.kmp.designsystem.RedactionReveal
import com.siddharth.kmp.designsystem.StepState
import com.siddharth.kmp.designsystem.StepTimeline
import com.siddharth.kmp.designsystem.TimelineStep
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Deterministic Compose screenshot tests on the JVM (Robolectric, no emulator). Baselines live in
 * `docs/screenshots/` and diff cleanly in PRs — they double as the README's visual reference.
 *
 * Record/refresh:  ./gradlew :app:recordRoborazziDebug
 * Verify in CI:    ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application (not PaymentsLabApplication) — these render pure design-system composables and
// must not boot the real Koin graph (which would throw "already started" across tests).
@Config(sdk = [34], application = android.app.Application::class)
class ScreenshotCatalogTest {
    @get:Rule
    val compose = createComposeRule()

    private fun snapshot(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            PaymentsLabTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.width(360.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(Modifier.padding(DesignTokens.Spacing.lg)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    /** For a full screen (its own Scaffold/topbar/padding) rather than an isolated component. */
    private fun snapshotScreen(
        name: String,
        reducedMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                PaymentsLabTheme {
                    Surface(modifier = Modifier.width(360.dp).height(720.dp)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private fun sampleTimeline() =
        persistentListOf(
            TimelineStep(
                title = "Order created",
                subtitle = "₹499 · price resolved server-side",
                state = StepState.DONE,
                payload = persistentListOf("order_id" to "order_9f3c", "amount" to "49900 INR"),
            ),
            TimelineStep(
                title = "Launching Razorpay",
                subtitle = null,
                state = StepState.DONE,
                payload = persistentListOf("key_id" to "rzp_test_ab••••yz"),
            ),
            TimelineStep(
                title = "Client returned: Success",
                subtitle = "a hint — not yet trusted",
                state = StepState.DONE,
                payload = persistentListOf("payment_id" to "pay_Kx1", "signature" to "9f••••3a"),
            ),
            TimelineStep(
                title = "Verifying with server",
                subtitle = "HMAC-SHA256 signature check",
                state = StepState.ACTIVE,
                payload = persistentListOf(),
            ),
            TimelineStep(
                title = "Settled",
                subtitle = "awaiting server verdict",
                state = StepState.PENDING,
                payload = persistentListOf(),
            ),
        )

    @Test
    fun stepTimeline_light() = snapshot("step_timeline_light") { StepTimeline(sampleTimeline()) }

    @Test
    fun stepTimeline_dark() = snapshot("step_timeline_dark", dark = true) { StepTimeline(sampleTimeline()) }

    @Test
    fun payloadCard() =
        snapshot("payload_card") {
            PayloadCard(
                title = "verify request",
                entries =
                    persistentListOf(
                        "gateway" to "razorpay",
                        "order_id" to "order_9f3c",
                        "signature" to "9f••••3a",
                    ),
            )
        }

    @Test
    fun gatewayBadges() =
        snapshot("gateway_badges") {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
                SectionHeader("Provider status")
                GatewayStatusBadge(GatewayStatusUi.SANDBOX_READY)
                GatewayStatusBadge(GatewayStatusUi.KYC_GATED)
                GatewayStatusBadge(GatewayStatusUi.COMING_SOON)
            }
        }

    @Test
    fun primaryButton() =
        snapshot("primary_button") {
            PrimaryButton(text = "Pay ₹499 (sandbox)", onClick = {}, modifier = Modifier.fillMaxWidth())
        }

    // reducedMotion = true: MOCK_MODE's shimmer is an infiniteRepeatable transition, which by
    // definition never settles - compose-ui-test's idling can't wait it out, so the captured frame
    // is whatever the transition happened to be on, nondeterministic run to run. Forcing reduced
    // motion (which the shimmer modifier already checks) captures the static, correct baseline.
    @Test
    fun mockModeBadgeShimmer() =
        snapshot("mock_mode_badge_shimmer") {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                GatewayStatusBadge(GatewayStatusUi.MOCK_MODE)
            }
        }

    @Test
    fun successBurst() = snapshot("success_burst") { SuccessBurst() }

    // reducedMotion = true: FailureShake's shake is a real (non-infinite) Animatable tween, but its
    // LaunchedEffect races the initial capture under Robolectric - same nondeterminism class as
    // mockModeBadgeShimmer above. FailureShake already checks LocalReducedMotion for exactly this.
    @Test
    fun failureShake() =
        snapshot("failure_shake") {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                FailureShake()
            }
        }

    @Test
    fun animatedAmount() = snapshot("animated_amount") { AnimatedAmount(amountMinor = 49_900L, currency = "INR") }

    // reducedMotion = true: RedactionReveal's scramble uses unseeded kotlin.random.Random per
    // frame, and its LaunchedEffect drives that scramble via a raw delay() loop rather than the
    // Compose animation clock — so compose-ui-test's idling can't reliably wait for it to settle
    // under Robolectric, making a mid-scramble (random) frame get captured nondeterministically.
    // Forcing reduced motion skips straight to the settled `value` string: deterministic, and
    // still the correct baseline for a still image (a screenshot can't show motion anyway).
    @Test
    fun redactionReveal() =
        snapshot("redaction_reveal") {
            // RedactionReveal takes reducedMotion as an explicit parameter (not a CompositionLocal
            // read) since the :designsystem extraction — pass it directly instead of the
            // CompositionLocalProvider wrapper the other reducedMotion-dependent snapshots use.
            RedactionReveal(value = "9f••••3a", reducedMotion = true)
        }

    @Test
    fun paymentFlowDiagram_unverified() =
        snapshot("payment_flow_diagram_unverified") {
            PaymentFlowDiagram(activeHop = FlowHop.GATEWAY, verified = false)
        }

    @Test
    fun paymentFlowDiagram_verified() =
        snapshot("payment_flow_diagram_verified") {
            PaymentFlowDiagram(activeHop = FlowHop.WEBHOOK, verified = true)
        }

    // ── Composed ProviderLabScreen — catches layout issues the isolated component shots miss ──────
    @Test
    fun providerLabScreen_running() =
        snapshotScreen("provider_lab_screen_running") {
            ProviderLabScreen(
                state =
                    ProviderLabUiState(
                        steps = sampleTimeline(),
                        isRunning = true,
                        currentHop = FlowHop.GATEWAY,
                        verified = false,
                    ),
                providerName = "Paystack",
                priceLabel = "₹149",
                onPay = {},
                onBack = {},
            )
        }

    @Test
    fun providerLabScreen_settledSuccess() =
        snapshotScreen("provider_lab_screen_settled_success") {
            ProviderLabScreen(
                state =
                    ProviderLabUiState(
                        steps = sampleTimeline(),
                        isRunning = false,
                        hasRun = true,
                        finalStatus = PaymentStatus.SUCCESS,
                        currentHop = FlowHop.BACKEND,
                        verified = true,
                    ),
                providerName = "Paystack",
                priceLabel = "₹149",
                onPay = {},
                onBack = {},
            )
        }

    // ── LabHomeScreen (B4 catalog) — region map + search + status-sectioned rows ────────────────
    // reducedMotion = true: the Paystack row below is MOCK_MODE, whose badge shimmer is an
    // infiniteRepeatable transition - same nondeterminism as mockModeBadgeShimmer above.
    @Test
    fun labHomeScreen_catalog() =
        snapshotScreen("lab_home_screen_catalog", reducedMotion = true) {
            LabHomeScreen(
                state =
                    LabHomeUiState(
                        allProviders =
                            listOf(
                                ProviderRow(
                                    id = GatewayId("razorpay"),
                                    displayName = "Razorpay",
                                    status = GatewayStatusUi.SANDBOX_READY,
                                    region = "India",
                                    blurb = "Real Razorpay Checkout SDK, sandbox-ready.",
                                    capabilities = persistentListOf("One-time", "UPI"),
                                ),
                                ProviderRow(
                                    id = GatewayId("paystack"),
                                    displayName = "Paystack",
                                    status = GatewayStatusUi.MOCK_MODE,
                                    region = "Africa",
                                    blurb = "Hosted checkout via Paystack's Standard Checkout.",
                                    capabilities = persistentListOf("One-time", "Cards"),
                                ),
                                ProviderRow(
                                    id = GatewayId("cybersource"),
                                    displayName = "Cybersource",
                                    status = GatewayStatusUi.COMING_SOON,
                                    region = "Global",
                                    blurb = "Flex Microform tokenization — not yet wired up.",
                                    capabilities = persistentListOf("One-time"),
                                ),
                            ).toImmutableList(),
                        selectedStatuses = persistentSetOf(),
                        selectedRegions = persistentSetOf(),
                    ),
                onOpenProvider = {},
            )
        }

    // ── HomeScreen (redesign) — gradient hero card, animated stats, recent activity ─────────────
    @Test
    fun homeScreen_dashboard() =
        snapshotScreen("home_screen_dashboard") {
            HomeScreen(
                state =
                    HomeUiState(
                        gatewayCount = 68,
                        successRatePercent = 94,
                        recentActivity =
                            persistentListOf(
                                RecentActivityRow("order_a1", "book_499", PaymentStatus.SUCCESS),
                                RecentActivityRow("order_b2", "coffee_149", PaymentStatus.SUCCESS),
                                RecentActivityRow("order_c3", "headphones_2499", PaymentStatus.FAILED),
                            ),
                    ),
                onOpenExplore = {},
                onOpenActivity = {},
            )
        }

    // ── GatewayBranding (redesign) — real bundled logos next to the deterministic monogram
    // fallback for gateways with no curated asset. Renders the same Logo/Monogram distinction
    // LabHomeScreen's ProviderCard uses, via the public GatewayBranding API directly (the actual
    // badge composable is private to feature:lab). ────────────────────────────────────────────
    @Composable
    private fun BrandBadgeRow(
        id: String,
        displayName: String,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            when (val asset = GatewayBranding.forId(id, displayName)) {
                is GatewayBrandAsset.Logo ->
                    Icon(
                        imageVector = asset.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Unspecified,
                    )
                is GatewayBrandAsset.Monogram ->
                    Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = asset.color) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = asset.letter.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
            }
            Text(text = displayName, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Test
    fun gatewayBrandBadges() =
        snapshot("gateway_brand_badges") {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)) {
                SectionHeader("Real logos + generated fallback")
                BrandBadgeRow("stripe", "Stripe")
                BrandBadgeRow("paypal", "PayPal")
                BrandBadgeRow("upi_intent", "UPI")
                BrandBadgeRow("paytmaio", "Paytm All-in-One")
                BrandBadgeRow("wipay", "WiPay")
                BrandBadgeRow("araka", "Araka")
            }
        }

    // ── ShieldPulse (redesign) — the secure-screen motion primitive, as it appears on Provider
    // detail and Checkout ───────────────────────────────────────────────────────────────────────
    @Test
    fun shieldPulse() =
        snapshot("shield_pulse") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            ) {
                ShieldPulse()
                Text(
                    text = "Protected — screenshots and screen recording are blocked here",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

    // ── HistoryScreen (redesign) — status filter chips, one selected ────────────────────────────
    @Test
    fun historyScreen_withFilters() =
        snapshotScreen("history_screen_with_filters") {
            HistoryScreen(
                state =
                    HistoryUiState(
                        rows =
                            persistentListOf(
                                HistoryRow("order_a1", "book_499", "razorpay", "₹499.00", PaymentStatus.SUCCESS, 100L),
                                HistoryRow(
                                    "order_b2",
                                    "coffee_149",
                                    "upi_intent",
                                    "₹149.00",
                                    PaymentStatus.FAILED,
                                    200L,
                                ),
                            ),
                        isLoading = false,
                        selectedStatuses = persistentSetOf(PaymentStatus.SUCCESS),
                    ),
                onToggleStatusFilter = {},
            )
        }

    // ── CheckoutScreen (redesign) — a selected product/gateway, floating-elevation selection ───
    @Test
    fun checkoutScreen_orderSummary() =
        snapshotScreen("checkout_screen_order_summary") {
            CheckoutScreen(
                state =
                    CheckoutUiState(
                        gateways =
                            persistentListOf(
                                CheckoutGateway(GatewayId("razorpay"), "Razorpay"),
                                CheckoutGateway(GatewayId("upi_intent"), "UPI Intent"),
                            ),
                        selectedProduct = DEMO_PRODUCTS.first { it.catalogItemId == "book_499" },
                        selectedGatewayId = GatewayId("razorpay"),
                    ),
                onSelectProduct = {},
                onSelectGateway = {},
                onPay = {},
            )
        }

    // ── Checkout flow frames (paying → settled) — same screen, later states, for the flow GIF ────
    private fun checkoutStateBase() =
        CheckoutUiState(
            gateways =
                persistentListOf(
                    CheckoutGateway(GatewayId("razorpay"), "Razorpay"),
                    CheckoutGateway(GatewayId("upi_intent"), "UPI Intent"),
                ),
            selectedProduct = DEMO_PRODUCTS.first { it.catalogItemId == "book_499" },
            selectedGatewayId = GatewayId("razorpay"),
        )

    private fun doneTimeline() =
        sampleTimeline()
            .map { it.copy(state = StepState.DONE) }
            .toImmutableList()

    @Test
    fun checkoutScreen_paying() =
        snapshotScreen("checkout_screen_paying", reducedMotion = true) {
            CheckoutScreen(
                state = checkoutStateBase().copy(steps = sampleTimeline(), isRunning = true),
                onSelectProduct = {},
                onSelectGateway = {},
                onPay = {},
            )
        }

    @Test
    fun checkoutScreen_settledSuccess() =
        snapshotScreen("checkout_screen_settled_success", reducedMotion = true) {
            CheckoutScreen(
                state =
                    checkoutStateBase().copy(
                        steps = doneTimeline(),
                        isRunning = false,
                        finalStatus = PaymentStatus.SUCCESS,
                    ),
                onSelectProduct = {},
                onSelectGateway = {},
                onPay = {},
            )
        }

    // ── HistoryScreen unfiltered — the full journal before any filter chip is tapped (flow frame) ─
    @Test
    fun historyScreen_all() =
        snapshotScreen("history_screen_all") {
            HistoryScreen(
                state =
                    HistoryUiState(
                        rows =
                            persistentListOf(
                                HistoryRow("order_a1", "book_499", "razorpay", "₹499.00", PaymentStatus.SUCCESS, 100L),
                                HistoryRow(
                                    "order_b2",
                                    "coffee_149",
                                    "upi_intent",
                                    "₹149.00",
                                    PaymentStatus.FAILED,
                                    200L,
                                ),
                                HistoryRow(
                                    "order_c3",
                                    "headphones_2499",
                                    "stripe",
                                    "₹2,499.00",
                                    PaymentStatus.SUCCESS,
                                    300L,
                                ),
                                HistoryRow(
                                    "order_d4",
                                    "course_9999",
                                    "paystack",
                                    "₹9,999.00",
                                    PaymentStatus.PENDING,
                                    400L,
                                ),
                            ),
                        isLoading = false,
                        selectedStatuses = persistentSetOf(),
                    ),
                onToggleStatusFilter = {},
            )
        }
}
