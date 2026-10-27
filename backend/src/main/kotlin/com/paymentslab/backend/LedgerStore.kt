package com.paymentslab.backend

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In-memory double-entry ledger backing `provider:wallet` (the "internal rail" archetype) — mirrors
 * [PaymentStore]'s style: simple in-memory maps, swappable for a real DB later.
 *
 * Every movement is a [Transaction]: a set of [Entry] whose signed `amountMinor` sums to zero — debit
 * one account, credit another, never a bare balance write. [post] is idempotent (same key → same
 * txn, applied once); [balance] reads the current account balance.
 *
 * // concurrency ceiling is a single [ReentrantLock] guarding the whole read-check-write per
 * // post — fine for an in-memory demo with a handful of accounts; upgrade to per-account locks (or a
 * // real DB's row-level locking) if throughput across many accounts ever matters.
 */
class LedgerStore {
    enum class EntryType { DEBIT, CREDIT }

    data class Entry(
        val accountId: String,
        val type: EntryType,
        val amountMinor: Long,
    ) {
        init {
            require(amountMinor > 0) { "entry amountMinor must be positive, was $amountMinor" }
        }

        /** Signed amount: debit reduces the account, credit increases it. */
        val signed: Long get() = if (type == EntryType.DEBIT) -amountMinor else amountMinor
    }

    data class Transaction(
        val txnId: String,
        val idempotencyKey: String,
        val entries: List<Entry>,
        val createdAtEpochMs: Long,
    )

    class UnbalancedTransactionException(
        sum: Long,
    ) : IllegalArgumentException("Ledger entries must sum to zero, got $sum")

    class InsufficientFundsException(
        accountId: String,
    ) : IllegalStateException("Account $accountId has insufficient balance for this debit")

    private val balances = ConcurrentHashMap<String, Long>()
    private val txnsByKey = ConcurrentHashMap<String, Transaction>()
    private val lock = ReentrantLock()

    /** Seed/recharge an account balance for the demo (e.g. "top up the wallet"). */
    fun seed(
        accountId: String,
        amountMinor: Long,
    ) {
        lock.withLock {
            balances.merge(accountId, amountMinor, Long::plus)
        }
    }

    fun balance(accountId: String): Long = balances[accountId] ?: 0L

    /**
     * Idempotently post a balanced transaction. Replaying [idempotencyKey] returns the SAME
     * [Transaction] without moving any balance a second time. Rejects unbalanced entries and rejects
     * (without partial application) any debit that would overdraw its account.
     */
    fun post(
        idempotencyKey: String,
        entries: List<Entry>,
    ): Transaction {
        val sum = entries.sumOf { it.signed }
        if (sum != 0L) throw UnbalancedTransactionException(sum)

        lock.withLock {
            txnsByKey[idempotencyKey]?.let { return it }

            for (entry in entries) {
                if (entry.type == EntryType.DEBIT) {
                    val current = balances[entry.accountId] ?: 0L
                    if (current < entry.amountMinor) throw InsufficientFundsException(entry.accountId)
                }
            }

            for (entry in entries) {
                balances.merge(entry.accountId, entry.signed, Long::plus)
            }

            val txn =
                Transaction(
                    txnId = "ledger_txn_${java.util.UUID.randomUUID()}",
                    idempotencyKey = idempotencyKey,
                    entries = entries,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            txnsByKey[idempotencyKey] = txn
            return txn
        }
    }

    /**
     * Reverse a prior debit: credit [walletAccountId] and debit [holdingAccountId] back by
     * [amountMinor] — itself idempotent via [post].
     */
    fun refund(
        idempotencyKey: String,
        walletAccountId: String,
        holdingAccountId: String,
        amountMinor: Long,
    ): Transaction =
        post(
            idempotencyKey = idempotencyKey,
            entries =
                listOf(
                    Entry(holdingAccountId, EntryType.DEBIT, amountMinor),
                    Entry(walletAccountId, EntryType.CREDIT, amountMinor),
                ),
        )

    /** Debit [walletAccountId] and credit [holdingAccountId] by [amountMinor] — the "pay" movement. */
    fun debit(
        idempotencyKey: String,
        walletAccountId: String,
        holdingAccountId: String,
        amountMinor: Long,
    ): Transaction =
        post(
            idempotencyKey = idempotencyKey,
            entries =
                listOf(
                    Entry(walletAccountId, EntryType.DEBIT, amountMinor),
                    Entry(holdingAccountId, EntryType.CREDIT, amountMinor),
                ),
        )
}
