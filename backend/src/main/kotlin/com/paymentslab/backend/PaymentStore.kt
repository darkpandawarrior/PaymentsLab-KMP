package com.paymentslab.backend

import com.paymentslab.core.protocol.PaymentStatusDto
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, thread-safe source of truth for orders and their payment state, keyed by `orderId`.
 *
 * This is intentionally the simplest thing that works: [ConcurrentHashMap] for records +
 * [KeySetView] for a processed-webhook-event-id set (idempotency). It is swappable for a real DB
 * (Exposed/SQLite/Postgres) later — the public surface (createOrder / recordVerification /
 * applyWebhook / get) is deliberately small so a JDBC-backed implementation can drop straight in.
 */
class PaymentStore {
    /** One record per order. `amountMinor`/`currency` are captured server-side at creation time. */
    data class PaymentRecord(
        val orderId: String,
        val catalogItemId: String,
        val gatewayId: String,
        val amountMinor: Long,
        val currency: String,
        val status: PaymentStatusDto,
        val paymentId: String? = null,
        val providerRef: String? = null,
        val updatedAtEpochMs: Long,
        /** The provider session material returned to the client on creation — cached so an
         *  idempotency-key replay can return it without calling the provider adapter a second time
         *  (a second `createProviderOrder` call would itself mint a second live order upstream). */
        val providerParams: Map<String, String> = emptyMap(),
    )

    private val records = ConcurrentHashMap<String, PaymentRecord>()

    /** Deduplication set of already-processed webhook event ids. */
    private val processedEventIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Maps a client [CreateOrderRequest.idempotencyKey] to the orderId it first minted. */
    private val idempotencyKeyToOrderId = ConcurrentHashMap<String, String>()

    /** Result of an idempotent order-creation attempt. */
    data class OrderCreationResult(
        val record: PaymentRecord,
        /** False if [idempotencyKey] was already used — caller must NOT re-invoke the provider
         *  adapter in that case; the cached [PaymentRecord.providerParams] is the answer. */
        val isNew: Boolean,
    )

    /**
     * Creates an order, or returns the existing one if [idempotencyKey] was already used — a retried
     * `POST /orders` for the same logical attempt must never mint a second live order (double charge).
     *
     * The `putIfAbsent`-style guard makes this atomic: under a concurrent double-submit with the same
     * key, only one caller wins the insert and both callers observe the same [PaymentRecord].
     */
    fun createOrder(
        orderId: String,
        catalogItemId: String,
        gatewayId: String,
        amountMinor: Long,
        currency: String,
        idempotencyKey: String,
    ): OrderCreationResult {
        // Write the record BEFORE publishing the idempotencyKey->orderId mapping — otherwise a
        // concurrent loser could observe the mapping and look up `records[orderId]` before this
        // writer's `records[orderId] = record` below has landed.
        val record =
            PaymentRecord(
                orderId = orderId,
                catalogItemId = catalogItemId,
                gatewayId = gatewayId,
                amountMinor = amountMinor,
                currency = currency,
                status = PaymentStatusDto.CREATED,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        records[orderId] = record

        val existingOrderId = idempotencyKeyToOrderId.putIfAbsent(idempotencyKey, orderId)
        if (existingOrderId != null) {
            // Someone else won the race for this key — this caller's speculative record above is
            // orphaned in `records` (harmless: nothing else ever looks it up by its unique orderId).
            // leaves one dead entry per lost race; fine for an in-memory demo store, revisit
            // if this ever backs a real DB with retention/GC concerns.
            val existing =
                requireNotNull(records[existingOrderId]) {
                    "idempotencyKey $idempotencyKey mapped to $existingOrderId but no record exists"
                }
            return OrderCreationResult(existing, isNew = false)
        }
        return OrderCreationResult(record, isNew = true)
    }

    /** Attaches the provider session material once it's known (after the adapter call). */
    fun recordProviderParams(
        orderId: String,
        providerParams: Map<String, String>,
    ) {
        records.computeIfPresent(orderId) { _, existing -> existing.copy(providerParams = providerParams) }
    }

    fun get(orderId: String): PaymentRecord? = records[orderId]

    /**
     * Apply the outcome of a client-initiated verify. Merges the new status/paymentId/providerRef
     * into the existing record. No-op returning null if the order is unknown.
     */
    fun recordVerification(
        orderId: String,
        status: PaymentStatusDto,
        paymentId: String?,
        providerRef: String? = null,
    ): PaymentRecord? =
        records.computeIfPresent(orderId) { _, existing ->
            existing.copy(
                status = status,
                paymentId = paymentId ?: existing.paymentId,
                providerRef = providerRef ?: existing.providerRef,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }

    /** Result of an idempotent webhook application. */
    data class WebhookResult(
        val duplicate: Boolean,
        val record: PaymentRecord?,
    )

    /**
     * Idempotently apply a webhook event. If [eventId] was already processed, returns
     * `duplicate = true` and leaves state UNCHANGED. Otherwise records the event id and applies
     * the new status (if the order exists).
     */
    fun applyWebhook(
        eventId: String,
        orderId: String,
        status: PaymentStatusDto,
        paymentId: String?,
        providerRef: String? = null,
    ): WebhookResult {
        // add() returns false if the id was already present → duplicate, do NOT mutate state.
        val firstTime = processedEventIds.add(eventId)
        if (!firstTime) {
            return WebhookResult(duplicate = true, record = records[orderId])
        }
        val updated = recordVerification(orderId, status, paymentId, providerRef)
        return WebhookResult(duplicate = false, record = updated)
    }
}
