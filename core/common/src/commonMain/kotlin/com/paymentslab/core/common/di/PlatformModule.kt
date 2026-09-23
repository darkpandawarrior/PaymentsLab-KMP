package com.paymentslab.core.common.di

import org.koin.core.module.Module

/**
 * Platform-specific Koin bindings, lifted from Doori's `core:platform` shape. Empty on every
 * platform today — `PaymentsLabApplication` still wires its full module list directly (see
 * `app/PaymentsLabApplication.kt`) — this exists so [initKoin] compiles across targets and a future
 * `iosApp` entry point (B8) has a real bootstrap to call instead of duplicating `startKoin` logic.
 */
expect fun platformModule(): Module
