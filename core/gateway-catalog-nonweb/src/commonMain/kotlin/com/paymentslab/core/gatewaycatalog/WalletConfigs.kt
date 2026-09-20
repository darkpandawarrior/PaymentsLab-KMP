package com.paymentslab.core.gatewaycatalog

import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.provider.wallet.WalletConfig

/**
 * Archetype-E: an internal-rail wallet backed by the backend's double-entry ledger, no external
 * SDK. One demo user account is enough to exercise balance checks, idempotent debits, and refunds
 * end-to-end — see `docs/providers/wallet.md`.
 */
val walletConfig =
    WalletConfig(
        gatewayId = GatewayId("wallet"),
        displayName = "Wallet",
        walletAccountId = "wallet_demo_user",
        docsPath = "docs/providers/wallet.md",
        blurb = "Internal wallet — a backend double-entry ledger, no external SDK or sandbox.",
    )
