package com.paymentslab.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paymentslab.core.designsystem.AppShell
import com.paymentslab.core.designsystem.AppShellDestination
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.feature.checkoutdemo.CheckoutRoot
import com.paymentslab.feature.history.HistoryRoot
import com.paymentslab.feature.home.HomeRoot
import com.paymentslab.feature.lab.LabHomeRoot
import com.paymentslab.feature.lab.ProviderLabRoot
import com.paymentslab.feature.lab.ai.LabSettingsRoot
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import com.siddharth.kmp.paymentsapi.PaymentHost
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutHost
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedGatewayConfig
import com.siddharth.kmp.provider.stripeconnect.StripeConnectCheckoutHost
import com.siddharth.kmp.security.SecureScreen
import org.koin.compose.koinInject

private val bottomBarDestinations =
    listOf(
        AppShellDestination("home", "Home", Icons.Filled.Home),
        AppShellDestination("explore", "Explore", Icons.Filled.Search),
        AppShellDestination("activity", "Activity", Icons.Filled.Receipt),
    )

/**
 * The app's navigation. Three bottom-bar destinations (Home, Explore, Activity) plus a center FAB
 * that opens Checkout, plus the `provider/{id}` detail route. Features are decoupled — this is the
 * only place their screens are composed together, and the real [PaymentHost] is threaded down to
 * the screens that launch payments.
 */
@Composable
fun AppNavHost(paymentHost: PaymentHost) {
    val navController = rememberNavController()
    val registry = koinInject<PaymentGatewayRegistry>()
    val hostedCheckoutRelay = koinInject<HostedCheckoutRelay>()
    val hostedGatewayConfigs = koinInject<List<HostedGatewayConfig>>()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedRoute =
        bottomBarDestinations
            .firstOrNull { dest ->
                currentDestination?.hierarchy?.any { it.route == dest.route } == true
            }?.route ?: "home"

    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(
            destinations = bottomBarDestinations,
            selectedRoute = selectedRoute,
            onSelectDestination = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onFabClick = { navController.navigate("checkout") },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
                enterTransition = {
                    slideInHorizontally(tween(DesignTokens.Motion.MEDIUM_MS)) { it / 4 } +
                        fadeIn(tween(DesignTokens.Motion.MEDIUM_MS))
                },
                exitTransition = {
                    slideOutHorizontally(tween(DesignTokens.Motion.MEDIUM_MS)) { -it / 4 } +
                        fadeOut(tween(DesignTokens.Motion.MEDIUM_MS))
                },
            ) {
                composable("home") {
                    HomeRoot(
                        onOpenExplore = {
                            navController.navigate("explore") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        },
                        onOpenActivity = {
                            navController.navigate("activity") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        },
                    )
                }
                composable("explore") {
                    LabHomeRoot(
                        onOpenProvider = { gatewayId -> navController.navigate("provider/${gatewayId.value}") },
                        onOpenSettings = { navController.navigate("lab-settings") },
                    )
                }
                composable("lab-settings") {
                    LabSettingsRoot(onBack = { navController.popBackStack() })
                }
                composable(
                    "provider/{id}",
                    enterTransition = {
                        scaleIn(tween(DesignTokens.Motion.MEDIUM_MS), initialScale = 0.9f) +
                            fadeIn(tween(DesignTokens.Motion.MEDIUM_MS))
                    },
                    exitTransition = {
                        scaleOut(tween(DesignTokens.Motion.MEDIUM_MS), targetScale = 0.9f) +
                            fadeOut(tween(DesignTokens.Motion.MEDIUM_MS))
                    },
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val meta = registry.byId(GatewayId(id))?.meta
                    // SecureScreen: block screenshots / screen-recording / recents-thumbnail on the
                    // payment-bearing screens, the way banking apps do.
                    SecureScreen {
                        ProviderLabRoot(
                            paymentHost = paymentHost,
                            gatewayId = GatewayId(id),
                            providerName = meta?.displayName ?: id,
                            priceLabel = "₹499",
                            catalogItemId = "book_499",
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    "checkout",
                    enterTransition = {
                        scaleIn(tween(DesignTokens.Motion.MEDIUM_MS), initialScale = 0.85f) +
                            fadeIn(tween(DesignTokens.Motion.MEDIUM_MS))
                    },
                    exitTransition = {
                        scaleOut(tween(DesignTokens.Motion.MEDIUM_MS), targetScale = 0.85f) +
                            fadeOut(tween(DesignTokens.Motion.MEDIUM_MS))
                    },
                ) {
                    SecureScreen {
                        CheckoutRoot(paymentHost = paymentHost, onBack = { navController.popBackStack() })
                    }
                }
                composable("activity") {
                    HistoryRoot()
                }
            }
        }

        // Overlays the active screen whenever a hosted-webview gateway (Paystack) is mid-checkout —
        // the archetype-C flow has no nav route of its own; it's driven by the relay instead.
        HostedCheckoutHost(
            relay = hostedCheckoutRelay,
            configs = hostedGatewayConfigs,
            modifier = Modifier.fillMaxSize(),
        )

        // Same overlay idiom for Stripe Connect's mock hosted-OAuth onboarding (roadmap #11) — shares
        // the one relay instance, keyed by its own gateway id so it never collides with a checkout.
        StripeConnectCheckoutHost(relay = hostedCheckoutRelay, modifier = Modifier.fillMaxSize())
    }
}
