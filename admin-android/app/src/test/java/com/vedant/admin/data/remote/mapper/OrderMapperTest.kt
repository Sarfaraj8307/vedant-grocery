package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.OrderDto
import com.vedant.admin.data.remote.dto.OrderItemDto
import com.vedant.admin.data.remote.dto.OrderStatusHistoryDto
import com.vedant.admin.domain.model.OrderStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13 acceptance: `delivery_slot_id` present ⇒ used; absent ⇒ `slot_id`; money
 * strings parse exactly; an unknown status is tolerated (no crash).
 */
class OrderMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dto(
        status: String? = "PENDING",
        deliverySlotId: String? = null,
        slotId: String? = null,
        finalTotal: Money? = Money(15_200),
        originalTotal: Money? = Money(15_200),
        orderItems: List<OrderItemDto>? = null,
    ) = OrderDto(
        id = "ORD-261011-ABD95",
        userId = "4bf3e5a5-2a93-44e7-b4f7-6e1e71180299",
        status = status,
        deliverySlotId = deliverySlotId,
        slotId = slotId,
        finalTotal = finalTotal,
        originalTotal = originalTotal,
        orderItems = orderItems,
    )

    @Test
    fun `delivery_slot_id is preferred when present`() {
        val order = dto(deliverySlotId = "slot-new", slotId = "slot-legacy").toDomain()
        assertEquals("slot-new", order.deliverySlotId)
    }

    @Test
    fun `slot_id is the fallback when delivery_slot_id is absent`() {
        val order = dto(deliverySlotId = null, slotId = "slot-legacy").toDomain()
        assertEquals("slot-legacy", order.deliverySlotId)
    }

    @Test
    fun `both absent yields null`() {
        assertNull(dto().toDomain().deliverySlotId)
    }

    @Test
    fun `a known status maps to the enum`() {
        assertEquals(OrderStatus.CONFIRMED, dto(status = "CONFIRMED").toDomain().status)
    }

    @Test
    fun `an unknown status is tolerated and the raw value is kept`() {
        val order = dto(status = "SOMETHING_NEW").toDomain()
        assertNull(order.status)
        assertEquals("SOMETHING_NEW", order.statusRaw)
        assertEquals("SOMETHING_NEW", order.statusLabel)
    }

    @Test
    fun `a null status maps to null and empty raw`() {
        val order = dto(status = null).toDomain()
        assertNull(order.status)
        assertEquals("", order.statusRaw)
    }

    @Test
    fun `money parses an integer, a one-decimal and a quoted two-decimal value`() {
        val decoded = json.decodeFromString<OrderDto>(
            """
            { "id": "o1", "status": "PENDING",
              "original_total": 152,
              "final_total": 152.0,
              "adjustment_amount": "0.00",
              "delivery_charge": "20.00" }
            """.trimIndent(),
        )
        val order = decoded.toDomain()
        assertEquals(Money(15_200), order.originalTotal)
        assertEquals(Money(15_200), order.finalTotal)
        assertEquals(Money.ZERO, order.adjustmentAmount)
        assertEquals(Money(2_000), order.deliveryCharge)
    }

    @Test
    fun `a negative adjustment parses exactly`() {
        val decoded = json.decodeFromString<OrderDto>(
            """{ "id": "o1", "status": "CONFIRMED", "adjustment_amount": "-132.50", "final_total": "20.00" }""",
        )
        val order = decoded.toDomain()
        assertEquals(Money(-13_250), order.adjustmentAmount)
        assertEquals(Money(2_000), order.finalTotal)
        assertTrue(order.hasAdjustment)
    }

    @Test
    fun `an absent NOT NULL money column renders zero rather than throwing`() {
        val order = OrderDto(id = "o1", status = "PENDING").toDomain()
        assertEquals(Money.ZERO, order.finalTotal)
    }

    @Test
    fun `qty is read from the qty field and never from quantity`() {
        val decoded = json.decodeFromString<OrderDto>(
            """
            { "id": "o1", "status": "PENDING",
              "order_items": [ { "id": "i1", "product_name": "Tomato", "qty": 3, "unit_price": "44.00", "line_total": "132.00" } ] }
            """.trimIndent(),
        )
        val item = decoded.toDomain().items.single()
        assertEquals(3, item.qty)
        assertEquals(Money(13_200), item.lineTotal)
        assertEquals("Tomato", item.productName)
    }

    @Test
    fun `item rejection fields map through`() {
        val item = OrderItemDto(
            id = "i1",
            productName = "Capsicum",
            qty = 2,
            unitPrice = Money(4_400),
            lineTotal = Money(8_800),
            rejected = true,
            rejectionReason = "damaged",
        ).toDomain()
        assertTrue(item.rejected)
        assertEquals("damaged", item.rejectionReason)
        assertEquals(2, item.qty)
    }

    @Test
    fun `an absent weight label maps to null`() {
        val item = OrderItemDto(id = "i1", productName = "Lemon", qty = 1).toDomain()
        assertNull(item.weightLabel)
    }

    @Test
    fun `timestamps parse to instants and junk becomes null`() {
        val order = OrderDto(
            id = "o1",
            status = "PENDING",
            createdAt = "2026-10-11T02:30:00+00:00",
            updatedAt = "not-a-date",
        ).toDomain()
        assertEquals(Instant.parse("2026-10-11T02:30:00Z"), order.createdAt)
        assertNull(order.updatedAt)
    }

    @Test
    fun `address snapshot is flattened to its primitive values without guessing keys`() {
        val snapshot = buildJsonObject {
            put("line1", "12 Main Street")
            put("city", "Pune")
            put("pincode", "411001")
            put("is_default", true)
            put("line2", "")
        }
        val order = OrderDto(id = "o1", status = "PENDING", addressSnapshot = snapshot).toDomain()
        assertEquals(listOf("12 Main Street", "Pune", "411001"), order.addressLines)
    }

    /**
     * `phone` and `name` live in the same snapshot as the postal address. Before
     * this was separated, the generic flatten printed the customer's phone
     * number as if it were part of the street address.
     */
    @Test
    fun `contact fields are excluded from address lines and surfaced as the phone`() {
        val snapshot = buildJsonObject {
            put("name", "Test Customer")
            put("phone", "9999900000")
            put("line1", "12 Test Street")
            put("city", "Pune")
            put("pincode", "411001")
        }
        val order = OrderDto(id = "o1", status = "PENDING", addressSnapshot = snapshot).toDomain()

        assertEquals(listOf("12 Test Street", "Pune", "411001"), order.addressLines)
        assertEquals("9999900000", order.customerPhone)
    }

    @Test
    fun `phone is normalised to a dialable form`() {
        val snapshot = buildJsonObject { put("phone", "+91 98765-43210") }
        val order = OrderDto(id = "o1", status = "PENDING", addressSnapshot = snapshot).toDomain()
        assertEquals("+919876543210", order.customerPhone)
    }

    @Test
    fun `a snapshot without a usable phone yields null so the call button is hidden`() {
        val noPhone = buildJsonObject { put("line1", "12 Test Street") }
        assertNull(OrderDto(id = "o1", addressSnapshot = noPhone).toDomain().customerPhone)

        // Too short to be a real number — do not offer a dead button.
        val junk = buildJsonObject { put("phone", "123") }
        assertNull(OrderDto(id = "o2", addressSnapshot = junk).toDomain().customerPhone)
    }

    @Test
    fun `history entries map with tolerant status decode`() {        val event = OrderStatusHistoryDto(
            id = "h1",
            oldStatus = "PENDING",
            newStatus = "CONFIRMED",
            reason = null,
            createdAt = "2026-10-11T03:00:00Z",
        ).toDomain()
        assertEquals(OrderStatus.PENDING, event.fromStatus)
        assertEquals(OrderStatus.CONFIRMED, event.toStatus)
        assertEquals("CONFIRMED", event.toStatusRaw)
    }

    @Test
    fun `toSummary uses the embedded item count and never guesses`() {
        val withItems = dto(orderItems = listOf(OrderItemDto(id = "a"), OrderItemDto(id = "b"))).toSummary()
        assertEquals(2, withItems.itemCount)
        assertEquals(Money(15_200), withItems.finalTotal)

        val withoutItems = dto(orderItems = null).toSummary()
        assertEquals(0, withoutItems.itemCount)
    }

    @Test
    fun `toSummary keeps the raw status for an unknown value`() {
        val summary = dto(status = "WEIRD").toSummary()
        assertNull(summary.status)
        assertEquals("WEIRD", summary.statusRaw)
    }
}
