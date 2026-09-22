package com.paymentslab.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the double-entry ledger backing `provider:wallet` — no Ktor server involved. */
class LedgerStoreTest {
    private val wallet = "wallet_user1"
    private val holding = WalletHoldingAccountId

    @Test
    fun `unbalanced transaction is rejected`() {
        val ledger = LedgerStore()
        ledger.seed(wallet, 10_000)

        assertThrows(LedgerStore.UnbalancedTransactionException::class.java) {
            ledger.post(
                idempotencyKey = "bad_1",
                entries =
                    listOf(
                        LedgerStore.Entry(wallet, LedgerStore.EntryType.DEBIT, 500),
                        LedgerStore.Entry(holding, LedgerStore.EntryType.CREDIT, 400),
                    ),
            )
        }
        assertEquals(10_000, ledger.balance(wallet))
    }

    @Test
    fun `balanced transaction is applied`() {
        val ledger = LedgerStore()
        ledger.seed(wallet, 10_000)

        ledger.debit("txn_1", wallet, holding, 3_000)

        assertEquals(7_000, ledger.balance(wallet))
        assertEquals(3_000, ledger.balance(holding))
    }

    @Test
    fun `idempotent debit applies exactly once when replayed`() {
        val ledger = LedgerStore()
        ledger.seed(wallet, 10_000)

        val first = ledger.debit("dup_key", wallet, holding, 2_000)
        val second = ledger.debit("dup_key", wallet, holding, 2_000)

        assertEquals(first.txnId, second.txnId)
        assertEquals(8_000, ledger.balance(wallet))
    }

    @Test
    fun `insufficient funds are rejected cleanly with no partial application`() {
        val ledger = LedgerStore()
        ledger.seed(wallet, 1_000)

        assertThrows(LedgerStore.InsufficientFundsException::class.java) {
            ledger.debit("over_1", wallet, holding, 5_000)
        }
        assertEquals(1_000, ledger.balance(wallet))
        assertEquals(0, ledger.balance(holding))
    }

    @Test
    fun `refund credits the wallet back and is itself idempotent`() {
        val ledger = LedgerStore()
        ledger.seed(wallet, 10_000)
        ledger.debit("pay_1", wallet, holding, 4_000)

        val r1 = ledger.refund("refund_1", wallet, holding, 4_000)
        val r2 = ledger.refund("refund_1", wallet, holding, 4_000)

        assertEquals(r1.txnId, r2.txnId)
        assertEquals(10_000, ledger.balance(wallet))
        assertEquals(0, ledger.balance(holding))
    }

    /** N concurrent posts with the SAME idempotency key ⇒ exactly one debit applied. */
    @Test
    fun `concurrent replays of the same idempotency key debit exactly once`() =
        runTest {
            val ledger = LedgerStore()
            ledger.seed(wallet, 10_000)

            withContext(Dispatchers.Default) {
                (1..8)
                    .map { async { ledger.debit("race_key", wallet, holding, 1_000) } }
                    .awaitAll()
            }

            assertEquals(9_000, ledger.balance(wallet))
            assertEquals(1_000, ledger.balance(holding))
        }

    /** N concurrent posts with DIFFERENT keys against a limited balance ⇒ balance never goes negative. */
    @Test
    fun `concurrent debits with different keys never overdraw the wallet`() =
        runTest {
            val ledger = LedgerStore()
            ledger.seed(wallet, 5_000)

            val results =
                withContext(Dispatchers.Default) {
                    (1..8)
                        .map { i ->
                            async {
                                runCatching { ledger.debit("race_key_$i", wallet, holding, 1_000) }
                            }
                        }.awaitAll()
                }

            val succeeded = results.count { it.isSuccess }
            assertEquals(5, succeeded)
            assertTrue(ledger.balance(wallet) >= 0)
            assertEquals(5_000, ledger.balance(wallet) + ledger.balance(holding))
        }
}
