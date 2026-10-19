package com.paymentslab.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** One item in [AppShell]'s bottom bar. */
data class AppShellDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * PaymentsLab-KMP's shared app chrome: a bottom [NavigationBar] over [destinations] plus a center
 * "Pay" [FloatingActionButton] that launches Checkout. Shared `commonMain` composable so Android
 * and iOS render pixel-identical nav UI — each platform supplies its own back-stack mechanism via
 * [selectedRoute]/[onSelectDestination]/[onFabClick] and renders its own [content] for the current
 * screen. With [showChrome] false the bar and FAB are hidden (full-screen flows such as checkout).
 */
@Composable
fun AppShell(
    destinations: List<AppShellDestination>,
    selectedRoute: String,
    onSelectDestination: (String) -> Unit,
    onFabClick: () -> Unit,
    showChrome: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Scaffold(
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = dest.route == selectedRoute,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelectDestination(dest.route)
                            },
                            // The label below already names the tab; a description here made TalkBack read it twice.
                            icon = { Icon(imageVector = dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showChrome) {
                FloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onFabClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = "New checkout",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        content = content,
    )
}
