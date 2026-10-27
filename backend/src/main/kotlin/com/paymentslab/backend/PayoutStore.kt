package com.paymentslab.backend

import com.paymentslab.core.protocol.PayoutStatusDto
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, thread-safe source of truth for payouts, keyed by `payoutId`. Mirrors [PaymentStore]'s
 * shape (same [ConcurrentHashMap] + idempotency-key dedup pattern) for the Transfers/payout rail —
 * the first real payout rail in this app (roadmap #4), modeled with the same rigor as the order flow.
 *
 * Real payout rails require business KYC before they'll move money, so there is no live provider call
 * here at all (unlike [PaymentStore], which does call out to Paystack once real credentials exist) —
 * every payout stays `PENDING` until [markSettled] is invoked by the mock settlement webhook. That's
 * the honest MOCK_MODE/KYC_GATED shape: initiate → PENDING → settled via webhook, never a fake
 * instant success.
 */
class PayoutStore {
    data class PayoutRecord(
        val payoutId: String,
        val gatewayId: String,
        val recipientRef: String,
        val amountMinor: Long,
        val currency: String,
        val status: PayoutStatusDto,
        val updatedAtEpochMs: Long,
    )

    private val records = ConcurrentHashMap<String, PayoutRecord>()
    private val idempotencyKeyToPayoutId = ConcurrentHashMap<String, String>()

    data class PayoutCreationResult(
        val record: PayoutRecord,
        val isNew: Boolean,
    )

    /**
     * Creates a payout, or returns the existing one if [idempotencyKey] was already used — a retried
     * `POST /payouts` for the same logical attempt must never initiate a second live transfer.
     * Same `putIfAbsent`-race-safe shape as [PaymentStore.createOrder].
     */
    fun initiate(
        payoutId: String,
        gatewayId: String,
        recipientRef: String,
        amountMinor: Long,
        currency: String,
        idempotencyKey: String,
    ): PayoutCreationResult {
        val record =
            PayoutRecord(
                payoutId = payoutId,
                gatewayId = gatewayId,
                recipientRef = recipientRef,
                amountMinor = amountMinor,
                currency = currency,
                status = PayoutStatusDto.PENDING,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        records[payoutId] = record

        val existingPayoutId = idempotencyKeyToPayoutId.putIfAbsent(idempotencyKey, payoutId)
        if (existingPayoutId != null) {
            // orphans one speculative record per lost race, same tradeoff PaymentStore
            // already accepts for an in-memory demo store.
            val existing =
                requireNotNull(records[existingPayoutId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingPayoutId but no record exists"
                }
            return PayoutCreationResult(existing, isNew = false)
        }
        return PayoutCreationResult(record, isNew = true)
    }

    fun get(payoutId: String): PayoutRecord? = records[payoutId]

    /** Applied by the mock settlement webhook only — never by [initiate] itself. */
    fun markSettled(
        payoutId: String,
        status: PayoutStatusDto,
    ): PayoutRecord? =
        records.computeIfPresent(payoutId) { _, existing ->
            existing.copy(status = status, updatedAtEpochMs = System.currentTimeMillis())
        }
}
