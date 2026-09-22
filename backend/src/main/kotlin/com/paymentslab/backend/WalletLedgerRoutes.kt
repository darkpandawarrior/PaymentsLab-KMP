package com.paymentslab.backend

import com.siddharth.kmp.paymentsapi.WalletBalanceResponse
import com.siddharth.kmp.paymentsapi.WalletDebitRequest
import com.siddharth.kmp.paymentsapi.WalletRefundRequest
import com.siddharth.kmp.paymentsapi.WalletTransactionResponse
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Every wallet ledger movement nets against this single merchant/holding account. */
internal const val WalletHoldingAccountId = "holding"

/**
 * Routes for `provider:wallet` (archetype-E, "internal rail") — a double-entry ledger with no
 * external SDK. `{accountId}` is the user's wallet account id (e.g. `wallet_user1`); every debit
 * and refund nets against [WalletHoldingAccountId] so the ledger always balances.
 *
 * - `GET /wallet/{accountId}/balance` — pre-flight balance check ([provider.wallet.WalletGateway.prepare]).
 * - `POST /wallet/{accountId}/debit` — the "pay" movement, idempotent on `idempotencyKey`.
 * - `POST /wallet/{accountId}/refund` — the reversing credit, idempotent too.
 * - `POST /wallet/{accountId}/seed` — recharge the wallet for the demo (no idempotency needed; it's
 *   a one-sided top-up, not a ledger movement between accounts).
 *
 * ThrowsCount is suppressed by design, same as [Application.module]: the throws are per-route
 * request validation nested in routing lambdas, idiomatic Ktor rather than a complexity smell.
 */
@Suppress("ThrowsCount")
fun Route.walletLedgerRoutes(ledger: LedgerStore) {
    get("/wallet/{accountId}/balance") {
        val accountId =
            call.parameters["accountId"]
                ?: throw BadRequestException("missing_account_id", "accountId path param required")
        call.respond(WalletBalanceResponse(accountId, ledger.balance(accountId)))
    }

    post("/wallet/{accountId}/debit") {
        val accountId =
            call.parameters["accountId"]
                ?: throw BadRequestException("missing_account_id", "accountId path param required")
        val req = call.receive<WalletDebitRequest>()

        val txn =
            try {
                ledger.debit(req.idempotencyKey, accountId, WalletHoldingAccountId, req.amountMinor)
            } catch (e: LedgerStore.InsufficientFundsException) {
                throw BadRequestException("insufficient_funds", e.message.orEmpty(), e)
            }
        call.application.log.info("[wallet] debit account=$accountId txn=${txn.txnId} amount=${req.amountMinor}")
        call.respond(WalletTransactionResponse(txn.txnId, accountId, ledger.balance(accountId)))
    }

    post("/wallet/{accountId}/refund") {
        val accountId =
            call.parameters["accountId"]
                ?: throw BadRequestException("missing_account_id", "accountId path param required")
        val req = call.receive<WalletRefundRequest>()

        val txn =
            try {
                ledger.refund(req.idempotencyKey, accountId, WalletHoldingAccountId, req.amountMinor)
            } catch (e: LedgerStore.InsufficientFundsException) {
                throw BadRequestException("insufficient_funds", e.message.orEmpty(), e)
            }
        call.application.log.info("[wallet] refund account=$accountId txn=${txn.txnId} amount=${req.amountMinor}")
        call.respond(WalletTransactionResponse(txn.txnId, accountId, ledger.balance(accountId)))
    }

    post("/wallet/{accountId}/seed") {
        val accountId =
            call.parameters["accountId"]
                ?: throw BadRequestException("missing_account_id", "accountId path param required")
        val amountMinor =
            call.request.queryParameters["amountMinor"]?.toLongOrNull()
                ?: throw BadRequestException("missing_amount", "amountMinor query param required")
        ledger.seed(accountId, amountMinor)
        call.respond(WalletBalanceResponse(accountId, ledger.balance(accountId)))
    }
}
