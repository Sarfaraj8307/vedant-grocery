package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.OrderDto
import com.vedant.admin.data.remote.dto.OrderItemDto
import com.vedant.admin.data.remote.dto.OrderStatusHistoryDto
import com.vedant.admin.data.remote.dto.ProfileDto
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderItem
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.model.OrderSummary
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * DTO → domain mapping, and the **only** place the §10 legacy/duplicate column
 * policy is applied. No domain model, ViewModel or composable ever sees
 * `slot_id`, `delivery_fee`, `total` or `subtotal` — those names stop here.
 *
 * The functions are pure and SDK-free, so they are trivially unit-testable.
 */
private const val TAG = "OrderMapper"

/**
 * Maps a full order row (with items and history) to the domain [Order].
 *
 * ### Money
 * `final_total`, `original_total`, `adjustment_amount` and `delivery_charge` are
 * **NOT NULL** columns, so they map to non-null [Money] fields. If the server
 * ever omits one — a schema anomaly, not a nullable column — the mapper falls
 * back to zero **and logs a warning**, rather than silently inventing a value.
 * `subtotal` is genuinely unverified (BLOCKED), so it stays nullable.
 *
 * ### Timestamps
 * ISO-8601 strings become [Instant]; a malformed or absent value becomes `null`
 * rather than a fabricated time (§5.3).
 */
fun OrderDto.toDomain(): Order {
    val parsedStatus = OrderStatus.fromWire(status)
    return Order(
        id = id,
        status = parsedStatus,
        statusRaw = status.orEmpty(),
        originalTotal = originalTotal.orZero("original_total"),
        adjustmentAmount = adjustmentAmount ?: Money.ZERO,
        finalTotal = finalTotal.orZero("final_total"),
        deliveryCharge = (deliveryCharge ?: deliveryFee).orZero("delivery_charge"),
        subtotal = subtotal,
        // Pair 6: authoritative delivery_slot_id, legacy slot_id as fallback.
        deliverySlotId = deliverySlotId ?: slotId,
        customerRef = userId?.take(8),
        customerName = profiles.customerName(),
        addressLines = addressSnapshot.toAddressLines(),
        customerPhone = addressSnapshot.toCustomerPhone(),
        adjustmentReason = adjustmentReason,
        adjustedAt = parseInstantOrNull(adjustedAt),
        createdAt = parseInstantOrNull(placedAt ?: createdAt),
        updatedAt = parseInstantOrNull(updatedAt),
        items = orderItems?.map { it.toDomain() }.orEmpty(),
        history = statusHistory?.map { it.toDomain() }.orEmpty(),
    )
}

/**
 * Maps an order row (with an embedded `order_items(id)` count) to a list row.
 *
 * `itemCount` comes from the embedded children when the select asked for them,
 * and is `0` otherwise — the list never guesses a count.
 */
fun OrderDto.toSummary(): OrderSummary {
    val parsedStatus = OrderStatus.fromWire(status)
    return OrderSummary(
        id = id,
        status = parsedStatus,
        statusRaw = status.orEmpty(),
        customerRef = userId?.take(8),
        customerName = profiles.customerName(),
        createdAt = parseInstantOrNull(placedAt ?: createdAt),
        itemCount = orderItems?.size ?: 0,
        finalTotal = finalTotal.orZero("final_total"),
    )
}

fun OrderItemDto.toDomain(): OrderItem = OrderItem(
    id = id,
    productId = productId,
    productName = productName?.takeIf { it.isNotBlank() } ?: "Item",
    weightLabel = weightLabel?.takeIf { it.isNotBlank() },
    // `qty` is the live column; default 0 only when the column is genuinely absent.
    qty = qty ?: 0,
    unitPrice = unitPrice ?: Money.ZERO,
    lineTotal = lineTotal ?: Money.ZERO,
    rejected = rejected == true,
    rejectionReason = rejectionReason,
)

fun OrderStatusHistoryDto.toDomain(): OrderStatusEvent = OrderStatusEvent(
    id = id,
    fromStatus = OrderStatus.fromWire(oldStatus),
    fromStatusRaw = oldStatus?.takeIf { it.isNotBlank() },
    toStatus = OrderStatus.fromWire(newStatus),
    toStatusRaw = newStatus.orEmpty(),
    changedBy = changedBy,
    reason = reason,
    createdAt = parseInstantOrNull(createdAt),
)

// --- Helpers ----------------------------------------------------------------

private fun Money?.orZero(field: String): Money {
    if (this == null) {
        AppLog.w(TAG, "expected NOT NULL money column '$field' was absent; rendering 0")
        return Money.ZERO
    }
    return this
}

/** Tolerant ISO-8601 parse: junk or blank → `null`, never a fabricated instant. */
internal fun parseInstantOrNull(text: String?): Instant? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { Instant.parse(trimmed) }.getOrNull()
}

/**
 * Flattens the `address_snapshot` jsonb into display lines.
 *
 * The snapshot's exact shape is unverified (BLOCKED), so we do **not** guess key
 * names: we collect every primitive string/number value in document order and
 * skip nulls and booleans. This renders the address the customer entered without
 * inventing a schema.
 */
internal fun JsonElement?.toAddressLines(): List<String> {
    val root = this ?: return emptyList()
    val lines = mutableListOf<String>()
    fun visit(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                // `phone` and `name` are contact fields, not address lines. They
                // are surfaced separately (customerPhone / customerName); letting
                // the generic flatten swallow them printed the customer's phone
                // number as if it were part of the street address.
                if (key !in CONTACT_KEYS) visit(value)
            }
            is JsonArray -> element.forEach(::visit)
            is JsonNull -> Unit
            is JsonPrimitive -> {
                if (element.booleanOrNull == null) {
                    val text = element.contentOrNull?.trim().orEmpty()
                    if (text.isNotEmpty()) lines += text
                }
            }
        }
    }
    visit(root)
    return lines
}

/** Snapshot keys that carry contact identity rather than a postal address. */
private val CONTACT_KEYS = setOf("phone", "name")

/**
 * The customer's phone from `address_snapshot.phone`, normalised just enough to
 * be dialable: whitespace, dashes and brackets are stripped, a leading `+` is
 * kept. Returns `null` when there is nothing usable, so the caller hides the
 * call affordance instead of offering a dead button.
 */
internal fun JsonElement?.toCustomerPhone(): String? {
    val root = this as? JsonObject ?: return null
    val raw = (root["phone"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val normalised = buildString {
        raw.forEachIndexed { index, ch ->
            when {
                ch == '+' && index == 0 -> append(ch)
                ch.isDigit() -> append(ch)
            }
        }
    }
    // Anything shorter than 6 digits is not a real phone number.
    return normalised.takeIf { it.count(Char::isDigit) >= 6 }
}

/** First + last name, then username, then email — whichever the server supplied. */
private fun ProfileDto?.customerName(): String? {
    val profile = this ?: return null
    val fullName = listOfNotNull(
        profile.firstName?.takeIf { it.isNotBlank() },
        profile.lastName?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    if (fullName.isNotBlank()) return fullName
    return profile.username?.takeIf { it.isNotBlank() }
        ?: profile.email?.takeIf { it.isNotBlank() }
}
