package com.paymentslab.ios.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.paymentslab.core.designsystem.AppShell
import com.paymentslab.core.designsystem.AppShellDestination
import com.paymentslab.core.designsystem.PaymentsLabTheme
import com.paymentslab.feature.checkoutdemo.CheckoutRoot
import com.paymentslab.feature.history.HistoryRoot
import com.paymentslab.feature.home.HomeRoot
import com.paymentslab.feature.lab.LabHomeRoot
import com.paymentslab.feature.lab.ProviderLabRoot
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentHost

/**
 * A dummy [PaymentHost] — every gateway in the shared `:core:gateway-catalog` lists, plus the two
 * generic-archetype fallbacks in `IosGatewayConfigs.kt`, only needs the opaque marker, never an
 * Activity/ViewController-typed cast. The native-SDK archetype-A gateways that DO need a real host
 * go through their Swift `*CheckoutHost` instead (see `KoinInit.kt`).
 */
private object IosPaymentHost : PaymentHost

private val bottomBarDestinations =
    listOf(
        AppShellDestination("home", "Home", Icons.Filled.Home),
        AppShellDestination("explore", "Explore", Icons.Filled.Search),
        AppShellDestination("activity", "Activity", Icons.Filled.Receipt),
    )

/** iOS's screen state — a plain sealed hierarchy instead of Android's `NavController` back stack. */
private sealed interface IosScreen {
    data object Home : IosScreen

    data object Explore : IosScreen

    data class Provider(
        val gatewayId: GatewayId,
    ) : IosScreen

    data object Checkout : IosScreen

    data object Activity : IosScreen
}

/**
 * The iOS app's whole UI: Home / Explore (catalog → provider lab) / Activity, plus Checkout via
 * the shared [AppShell]'s center FAB — now at parity with Android's [AppShellDestination] set
 * (see Task 19 in `docs/superpowers/plans/2026-07-04-ui-ux-redesign.md`). Still `remember`-backed
 * state instead of `jb.navigation.compose`'s full graph (Navigation3 is Android-only), but the
 * screen set now matches Android's.
 */
@Composable
fun AppRoot() {
    PaymentsLabTheme {
        var screen by remember { mutableStateOf<IosScreen>(IosScreen.Home) }

        val selectedRoute =
            when (screen) {
                IosScreen.Home -> "home"
                // Provider detail is a child of Explore for tab-highlight purposes, mirroring
                // Android's NavDestination.hierarchy check on "provider/{id}" in AppNavHost.kt.
                IosScreen.Explore, is IosScreen.Provider -> "explore"
                IosScreen.Checkout -> "checkout"
                IosScreen.Activity -> "activity"
            }

        AppShell(
            destinations = bottomBarDestinations,
            selectedRoute = selectedRoute,
            onSelectDestination = { route ->
                screen =
                    when (route) {
                        "home" -> IosScreen.Home
                        "explore" -> IosScreen.Explore
                        "activity" -> IosScreen.Activity
                        else -> screen
                    }
            },
            onFabClick = { screen = IosScreen.Checkout },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val current = screen) {
                    IosScreen.Home ->
                        HomeRoot(
                            onOpenExplore = { screen = IosScreen.Explore },
                            onOpenActivity = { screen = IosScreen.Activity },
                        )
                    IosScreen.Explore ->
                        LabHomeRoot(onOpenProvider = { id -> screen = IosScreen.Provider(id) })
                    is IosScreen.Provider ->
                        ProviderLabRoot(
                            paymentHost = IosPaymentHost,
                            gatewayId = current.gatewayId,
                            providerName = current.gatewayId.value,
                            priceLabel = "₹149",
                            catalogItemId = "coffee_149",
                            onBack = { screen = IosScreen.Explore },
                        )
                    IosScreen.Checkout ->
                        CheckoutRoot(paymentHost = IosPaymentHost, onBack = { screen = IosScreen.Home })
                    IosScreen.Activity -> HistoryRoot()
                }
            }
        }
    }
}
