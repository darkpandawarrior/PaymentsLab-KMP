package com.paymentslab.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.paymentslab.core.gatewaycatalog.allHostedGatewayConfigs
import com.paymentslab.feature.checkoutdemo.CheckoutRoot
import com.paymentslab.feature.lab.LabHomeRoot
import com.paymentslab.feature.lab.ProviderLabRoot
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentHost

/** Opaque marker only — no gateway in the web build ever casts the host (same as iOS). */
private object WebPaymentHost : PaymentHost

/** The web preview's screen state — `remember`-backed like iOS's, no navigation graph needed. */
private sealed interface WebScreen {
    data object Explore : WebScreen

    data class Provider(
        val gatewayId: GatewayId,
    ) : WebScreen

    data object Checkout : WebScreen
}

private val bottomBarDestinations =
    listOf(
        AppShellDestination("explore", "Explore", Icons.Filled.Search),
    )

/**
 * The browser preview's whole UI: the gateway catalog (Explore → provider lab) plus the explained
 * checkout via the shared [AppShell] center FAB — the two surfaces the portfolio embed demoes.
 * Home/Activity are deliberately absent (Activity's journal is Room-backed, Android/iOS only).
 */
@Composable
fun WebAppRoot() {
    PaymentsLabTheme {
        var screen by remember { mutableStateOf<WebScreen>(WebScreen.Explore) }

        val selectedRoute =
            when (screen) {
                WebScreen.Explore, is WebScreen.Provider -> "explore"
                WebScreen.Checkout -> "checkout"
            }

        AppShell(
            destinations = bottomBarDestinations,
            selectedRoute = selectedRoute,
            onSelectDestination = { route -> if (route == "explore") screen = WebScreen.Explore },
            onFabClick = { screen = WebScreen.Checkout },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val current = screen) {
                    WebScreen.Explore ->
                        LabHomeRoot(onOpenProvider = { id -> screen = WebScreen.Provider(id) })
                    is WebScreen.Provider ->
                        ProviderLabRoot(
                            paymentHost = WebPaymentHost,
                            gatewayId = current.gatewayId,
                            providerName =
                                (allHostedGatewayConfigs + webOnlyHostedGatewayConfigs)
                                    .firstOrNull { it.gatewayId == current.gatewayId }
                                    ?.displayName ?: current.gatewayId.value,
                            priceLabel = "₹149",
                            catalogItemId = "coffee_149",
                            onBack = { screen = WebScreen.Explore },
                        )
                    WebScreen.Checkout ->
                        CheckoutRoot(paymentHost = WebPaymentHost, onBack = { screen = WebScreen.Explore })
                }
            }
        }
    }
}
