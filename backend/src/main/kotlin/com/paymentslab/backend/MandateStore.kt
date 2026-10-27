package com.paymentslab.backend

import com.paymentslab.core.protocol.MandateStatusDto
import com.paymentslab.core.protocol.PaymentStatusDto
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, thread-safe source of truth for mandates (roadmap #6 — Razorpay recurring) and their
 * debits. Mirrors [PayoutStore]'s shape: [ConcurrentHashMap] + idempotency-key dedup for both mandate
 * creation and each recurring debit.
 *
 * Honest mock: setup mints the mandate straight to ACTIVE (Razorpay authorizes the mandate itself via
 * the checkout SDK; there is no separate async authorization step to fake here, unlike a payout's
 * KYC-gated settlement). A real recurring *schedule* — charging automatically on a cadence — needs a
 * backend scheduler; this store only models a single on-demand debit call, which is what a scheduler
 * would invoke once per cycle.
 */
class MandateStore {
    data class MandateRecord(
        val mandateId: String,
        val catalogItemId: String,
        val gatewayId: String,
        val amountMinor: Long,
        val currency: String,
        val status: MandateStatusDto,
        val providerParams: Map<String, String> = emptyMap(),
        val updatedAtEpochMs: Long,
    )

    data class DebitRecord(
        val debitId: String,
        val mandateId: String,
        val amountMinor: Long,
        val currency: String,
        val status: PaymentStatusDto,
        val updatedAtEpochMs: Long,
    )

    private val mandates = ConcurrentHashMap<String, MandateRecord>()
    private val idempotencyKeyToMandateId = ConcurrentHashMap<String, String>()

    private val debits = ConcurrentHashMap<String, DebitRecord>()
    private val debitIdempotencyKeyToDebitId = ConcurrentHashMap<String, String>()

    data class MandateCreationResult(
        val record: MandateRecord,
        val isNew: Boolean,
    )

    /**
     * Creates a mandate (without provider params yet — same two-step shape [PaymentStore.createOrder]
     * + [PaymentStore.recordProviderParams] uses), or returns the existing one if [idempotencyKey] was
     * already used. Caller must only invoke the provider adapter when [MandateCreationResult.isNew].
     */
    fun createMandate(
        mandateId: String,
        catalogItemId: String,
        gatewayId: String,
        amountMinor: Long,
        currency: String,
        idempotencyKey: String,
    ): MandateCreationResult {
        val record =
            MandateRecord(
                mandateId = mandateId,
                catalogItemId = catalogItemId,
                gatewayId = gatewayId,
                amountMinor = amountMinor,
                currency = currency,
                status = MandateStatusDto.ACTIVE,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        mandates[mandateId] = record

        val existingMandateId = idempotencyKeyToMandateId.putIfAbsent(idempotencyKey, mandateId)
        if (existingMandateId != null) {
            // orphans one speculative record per lost race, same tradeoff PaymentStore/
            // PayoutStore already accept for an in-memory demo store.
            val existing =
                requireNotNull(mandates[existingMandateId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingMandateId but no record exists"
                }
            return MandateCreationResult(existing, isNew = false)
        }
        return MandateCreationResult(record, isNew = true)
    }

    /** Attaches the provider session material once it's known (after the adapter call). */
    fun recordProviderParams(
        mandateId: String,
        providerParams: Map<String, String>,
    ) {
        mandates.computeIfPresent(mandateId) { _, existing -> existing.copy(providerParams = providerParams) }
    }

    /** Cancels an active mandate — no further debits succeed against it once cancelled. */
    fun cancel(mandateId: String): MandateRecord? =
        mandates.computeIfPresent(mandateId) { _, existing ->
            existing.copy(status = MandateStatusDto.CANCELLED, updatedAtEpochMs = System.currentTimeMillis())
        }

    fun get(mandateId: String): MandateRecord? = mandates[mandateId]

    data class DebitResult(
        val record: DebitRecord,
        val isNew: Boolean,
    )

    /**
     * Charge one recurring debit against an ACTIVE mandate. Returns null if [mandateId] is unknown or
     * not ACTIVE — the caller maps that to a rejection. Idempotent on [idempotencyKey]: a replay
     * returns the same [DebitRecord] instead of charging twice.
     */
    fun debit(
        debitId: String,
        mandateId: String,
        idempotencyKey: String,
    ): DebitResult? {
        val mandate = mandates[mandateId] ?: return null
        if (mandate.status != MandateStatusDto.ACTIVE) return null

        val record =
            DebitRecord(
                debitId = debitId,
                mandateId = mandateId,
                amountMinor = mandate.amountMinor,
                currency = mandate.currency,
                status = PaymentStatusDto.SUCCESS,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        debits[debitId] = record

        val existingDebitId = debitIdempotencyKeyToDebitId.putIfAbsent(idempotencyKey, debitId)
        if (existingDebitId != null) {
            val existing =
                requireNotNull(debits[existingDebitId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingDebitId but no record exists"
                }
            return DebitResult(existing, isNew = false)
        }
        return DebitResult(record, isNew = true)
    }
}
