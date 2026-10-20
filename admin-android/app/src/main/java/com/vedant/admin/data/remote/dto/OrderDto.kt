package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire shape of an `orders` row, plus its embedded children when selected.
 *
 * **Column availability: BLOCKED (no admin creds — R1).** `orders` is
 * RLS-hidden, so its live column list could not be enumerated
 * (`docs/CONTRACT.md` §10). This DTO therefore declares the columns the
 * architecture and the customer PWA both rely on, keeps **both** slot columns
 * (pair 6: `delivery_slot_id` is authoritative, `slot_id` is the fallback), and
 * makes everything optional so an extra or renamed live column decodes to `null`
 * rather than throwing. Re-check after T02 enumerates the real columns.
 *
 * `status` is a `String`, not [com.vedant.admin.domain.model.OrderStatus], so an
 * unknown enum value is tolerated (§5.2).
 */
@Serializable
data class OrderDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("address_id") val addressId: String? = null,

    // --- Pair 6: both slot columns are carried; the mapper picks one. --------
    @SerialName("delivery_slot_id") val deliverySlotId: String? = null,
    @SerialName("slot_id") val slotId: String? = null,

    val status: String? = null,

    // --- Money (integer paise end-to-end; never a Double) --------------------
    @SerialName("original_total") @Serializable(with = MoneySerializer::class) val originalTotal: Money? = null,
    @SerialName("adjustment_amount") @Serializable(with = MoneySerializer::class) val adjustmentAmount: Money? = null,
    @SerialName("final_total") @Serializable(with = MoneySerializer::class) val finalTotal: Money? = null,
    @SerialName("delivery_charge") @Serializable(with = MoneySerializer::class) val deliveryCharge: Money? = null,
    @SerialName("delivery_fee") @Serializable(with = MoneySerializer::class) val deliveryFee: Money? = null,
    @SerialName("subtotal") @Serializable(with = MoneySerializer::class) val subtotal: Money? = null,
    @SerialName("total") @Serializable(with = MoneySerializer::class) val total: Money? = null,

    // --- Adjustment audit ----------------------------------------------------
    @SerialName("adjustment_reason") val adjustmentReason: String? = null,
    @SerialName("adjusted_by") val adjustedBy: String? = null,
    @SerialName("adjusted_at") val adjustedAt: String? = null,

    /** jsonb; shape unverified (BLOCKED), so kept raw and flattened in the mapper. */
    @SerialName("address_snapshot") val addressSnapshot: JsonElement? = null,

    /**
     * Live `orders` has **no** `created_at` — the order's placed timestamp is
     * `placed_at` (verified 2026-10-16 against project `hyibaljxicojfoxbihdn`).
     * `createdAt` is kept only as a legacy fallback.
     */
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,

    // --- Embedded children (present only when the select requests them) ------
    @SerialName("order_items") val orderItems: List<OrderItemDto>? = null,
    @SerialName("order_status_history") val statusHistory: List<OrderStatusHistoryDto>? = null,

    /** Optional customer embed; `null` when the relationship was not requested. */
    val profiles: ProfileDto? = null,
)
