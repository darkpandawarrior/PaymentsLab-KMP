package com.paymentslab.core.common.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatform

/**
 * Shared Koin bootstrap, lifted verbatim (pattern + re-entrancy guard) from Doori's
 * `core/ui/.../di/InitKoin.kt`. Always wires [platformModule] first, then the caller's [modules].
 *
 * Not yet called by `PaymentsLabApplication` (it still calls `startKoin` directly with its full
 * Android module list) — this is groundwork for the future `iosApp` entry point, which will call this
 * the same way Doori's `MainViewController` does. Wiring the Android app onto this is a mechanical,
 * behavior-preserving follow-up once there's a second caller to unify against.
 */
fun initKoin(
    modules: List<Module>,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    // Re-entrancy guard: an Application.onCreate and a re-created iOS view controller may both run.
    val alreadyStarted = runCatching { KoinPlatform.getKoin() }.isSuccess
    if (alreadyStarted) stopKoin()
    return startKoin {
        appDeclaration()
        modules(
            buildList {
                add(platformModule())
                addAll(modules)
            },
        )
    }
}
