package com.paymentslab.backend

import com.paymentslab.core.protocol.PaymentStatusDto
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, thread-safe source of truth for a Stripe-style Customer + saved instruments vault
 * (roadmap #7) — a modern retelling of the five-gateway `card_id` vault pattern. Mirrors
 * [PayoutStore]/[MandateStore]'s shape: [ConcurrentHashMap] + idempotency-key dedup for both saving
 * an instrument and charging one.
 *
 * Honest vault: the raw card token is never stored — only [InstrumentRecord.brand]/[last4] (what a
 * real Stripe Customer's PaymentMethod list would show) plus the token's opaque id. Charging a saved
 * instrument mints straight to SUCCESS, same honest-mock shape as [MandateStore.debit] — there is no
 * live Stripe API call to fake here, unlike a payout's KYC-gated settlement.
 */
class VaultStore {
    data class InstrumentRecord(
        val instrumentId: String,
        val customerId: String,
        val brand: String,
        val last4: String,
        val createdAtEpochMs: Long,
    )

    data class ChargeRecord(
        val chargeId: String,
        val customerId: String,
        val instrumentId: String,
        val amountMinor: Long,
        val currency: String,
        val status: PaymentStatusDto,
        val updatedAtEpochMs: Long,
    )

    // customerId -> (instrumentId -> record), so listing/charging never leaks across customers.
    private val instrumentsByCustomer = ConcurrentHashMap<String, ConcurrentHashMap<String, InstrumentRecord>>()
    private val idempotencyKeyToInstrumentId = ConcurrentHashMap<String, String>()

    private val charges = ConcurrentHashMap<String, ChargeRecord>()
    private val chargeIdempotencyKeyToChargeId = ConcurrentHashMap<String, String>()

    data class SaveResult(
        val record: InstrumentRecord,
        val isNew: Boolean,
    )

    /**
     * Saves a card token as a masked [InstrumentRecord], or returns the existing one if
     * [idempotencyKey] was already used. Same `putIfAbsent`-race-safe dedup as
     * [PaymentStore.createOrder].
     */
    fun saveInstrument(
        instrumentId: String,
        customerId: String,
        brand: String,
        last4: String,
        idempotencyKey: String,
    ): SaveResult {
        val customerInstruments = instrumentsByCustomer.computeIfAbsent(customerId) { ConcurrentHashMap() }
        // Claim the idempotency key BEFORE storing the record: unlike Payout/Mandate (looked up by id),
        // the vault LISTS a customer's instruments, so a speculative record stored before the dedup
        // check would leak into that list on a duplicate-key save.
        val existingInstrumentId = idempotencyKeyToInstrumentId.putIfAbsent(idempotencyKey, instrumentId)
        if (existingInstrumentId != null) {
            val existing =
                requireNotNull(customerInstruments[existingInstrumentId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingInstrumentId but no record exists"
                }
            return SaveResult(existing, isNew = false)
        }
        val record =
            InstrumentRecord(
                instrumentId = instrumentId,
                customerId = customerId,
                brand = brand,
                last4 = last4,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        // won the key race; store now. A concurrent loser could observe this a hair before
        // the write lands — acceptable for an in-memory demo store (per-account locks if it mattered).
        customerInstruments[instrumentId] = record
        return SaveResult(record, isNew = true)
    }

    fun list(customerId: String): List<InstrumentRecord> =
        instrumentsByCustomer[customerId]?.values?.sortedBy { it.createdAtEpochMs }.orEmpty()

    fun findInstrument(
        customerId: String,
        instrumentId: String,
    ): InstrumentRecord? = instrumentsByCustomer[customerId]?.get(instrumentId)

    data class ChargeResult(
        val record: ChargeRecord,
        val isNew: Boolean,
    )

    /**
     * Charges an order against a saved instrument. Returns null if [customerId]/[instrumentId] is
     * unknown — the caller maps that to a 404. Idempotent on [idempotencyKey]: a replay returns the
     * same [ChargeRecord] instead of charging twice.
     */
    fun charge(
        chargeId: String,
        customerId: String,
        instrumentId: String,
        amountMinor: Long,
        currency: String,
        idempotencyKey: String,
    ): ChargeResult? {
        findInstrument(customerId, instrumentId) ?: return null

        val record =
            ChargeRecord(
                chargeId = chargeId,
                customerId = customerId,
                instrumentId = instrumentId,
                amountMinor = amountMinor,
                currency = currency,
                status = PaymentStatusDto.SUCCESS,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        charges[chargeId] = record

        val existingChargeId = chargeIdempotencyKeyToChargeId.putIfAbsent(idempotencyKey, chargeId)
        if (existingChargeId != null) {
            val existing =
                requireNotNull(charges[existingChargeId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingChargeId but no record exists"
                }
            return ChargeResult(existing, isNew = false)
        }
        return ChargeResult(record, isNew = true)
    }
}
