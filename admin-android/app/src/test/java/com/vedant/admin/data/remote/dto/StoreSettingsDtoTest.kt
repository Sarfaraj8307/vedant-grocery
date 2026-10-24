package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Store-settings decode defect.
 *
 * THE BUG
 * `store_settings` is a singleton whose primary key is the **integer** `1`
 * (`id=1 CHECK id=1`) — both the customer PWA and this app target it with
 * `eq("id", 1)`. PostgREST therefore returns `{"id": 1}`.
 *
 * The DTO used to declare `val id: String?`, and kotlinx.serialization refuses to
 * decode a JSON number into a String. The exception escaped `readStoreSettings()`,
 * so `getSettings()` returned `Outcome.Failure` and the Store-settings screen
 * could not load at all — reported as "unable to open the store settings".
 *
 * These tests pin the wire shape that actually arrives, so the fix cannot
 * silently regress into a crash the next time the DTO is edited.
 */
class StoreSettingsDtoTest {

    /** Mirrors the client's configured JSON: tolerant of unknown keys. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `decodes the live row whose id is a JSON number`() {
        // The real PostgREST payload, including the columns added by later
        // migrations that this DTO does not model.
        val wire = """
            {
              "id": 1,
              "store_name": "Vedant Fruit & Vegetables",
              "phone": "+91 99999 99999",
              "whatsapp": "+91 99999 99999",
              "delivery_fee": 30.00,
              "free_delivery_threshold": 500.00,
              "min_order_amount": 0.00,
              "is_open": true,
              "updated_at": "2026-10-22T09:15:00.123456+00:00",
              "acceptance_reason": null,
              "acceptance_updated_at": null
            }
        """.trimIndent()

        val dto = json.decodeFromString<StoreSettingsDto>(wire)

        assertEquals("1", dto.id)
        assertEquals("Vedant Fruit & Vegetables", dto.storeName)
        assertEquals(Money(3_000), dto.deliveryFee)
        assertEquals(Money(50_000), dto.freeDeliveryThreshold)
        assertEquals(Money.ZERO, dto.minOrderAmount)
        assertEquals(true, dto.isOpen)
    }

    @Test
    fun `maps the numeric id through to the domain row the form saves against`() {
        val dto = json.decodeFromString<StoreSettingsDto>(
            """{"id": 1, "store_name": "Vedant", "is_open": true,
                "delivery_fee": 30, "free_delivery_threshold": 500, "min_order_amount": 0}""",
        )

        val domain = dto.toDomain()

        // A blank id would make `saveSettings` refuse with "row is missing", so
        // the id surviving the decode is the difference between a usable screen
        // and a dead one.
        assertEquals("1", domain.id)
        assertTrue(domain.id.isNotBlank())
    }

    @Test
    fun `still accepts a string id so a UUID primary key keeps working`() {
        val dto = json.decodeFromString<StoreSettingsDto>(
            """{"id": "8f14e45f-ceea-467f-a1b2-3c4d5e6f7a8b", "store_name": "Vedant"}""",
        )

        assertEquals("8f14e45f-ceea-467f-a1b2-3c4d5e6f7a8b", dto.id)
    }

    @Test
    fun `a missing id decodes to null rather than throwing`() {
        val dto = json.decodeFromString<StoreSettingsDto>("""{"store_name": "Vedant"}""")

        assertEquals(null, dto.id)
    }
}
