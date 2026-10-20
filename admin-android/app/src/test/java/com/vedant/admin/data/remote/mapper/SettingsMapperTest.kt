package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.StoreSettingsDto
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.domain.model.StoreSettingsDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T23 acceptance for the settings mapper, including the `store_name` mismatch
 * (PRD R6): the value is carried **as-is** and flagged, never rewritten.
 */
class SettingsMapperTest {

    @Test
    fun `the wrong live store name is flagged, not silently changed`() {
        val settings = StoreSettingsDto(id = "s1", storeName = "Fresh Mart").toDomain()

        assertEquals("Fresh Mart", settings.storeName)
        assertTrue(settings.isNameSuspect)
    }

    @Test
    fun `the correct name is not flagged`() {
        val settings = StoreSettingsDto(id = "s1", storeName = "Vedant Fruit & Vegetables").toDomain()
        assertFalse(settings.isNameSuspect)
    }

    @Test
    fun `a blank phone and whatsapp become null so the form shows empty`() {
        val settings = StoreSettingsDto(id = "s1", storeName = "x", phone = "", whatsapp = "").toDomain()

        assertNull(settings.phone)
        assertNull(settings.whatsapp)
    }

    @Test
    fun `money columns decode through the tolerant money parser`() {
        val settings = StoreSettingsDto(
            id = "s1",
            storeName = "x",
            deliveryFee = Money(2_000),
            freeDeliveryThreshold = Money(49_900),
            minOrderAmount = Money(10_000),
        ).toDomain()

        assertEquals(Money(2_000), settings.deliveryFee)
        assertEquals(Money(49_900), settings.freeDeliveryThreshold)
        assertEquals(Money(10_000), settings.minOrderAmount)
    }

    @Test
    fun `a missing money column renders zero rather than throwing`() {
        val settings = StoreSettingsDto(id = "s1", storeName = "x").toDomain()
        assertEquals(Money.ZERO, settings.deliveryFee)
    }

    @Test
    fun `the write payload carries only the editable columns and normalises blanks`() {
        val draft = StoreSettingsDraft(
            id = "s1",
            storeName = "  Vedant Fruit & Vegetables  ",
            phone = "  ",
            whatsapp = "98765 43210",
            isOpen = false,
            deliveryFee = Money(2_500),
            freeDeliveryThreshold = Money(50_000),
            minOrderAmount = Money(15_000),
        )

        val fields = draft.toWriteFields()

        assertEquals("Vedant Fruit & Vegetables", fields.storeName)
        assertNull("a blank phone is omitted, not sent as spaces", fields.phone)
        assertEquals("98765 43210", fields.whatsapp)
        assertEquals(false, fields.isOpen)
        assertEquals(Money(2_500), fields.deliveryFee)
    }

    @Test
    fun `a settings row round-trips through a draft`() {
        val settings = StoreSettings(
            id = "s1",
            storeName = "Vedant Fruit & Vegetables",
            phone = "020 1234 5678",
            whatsapp = null,
            isOpen = true,
            deliveryFee = Money(2_000),
            freeDeliveryThreshold = Money(49_900),
            minOrderAmount = Money(10_000),
            updatedAt = null,
        )

        val draft = settings.toDraft()

        assertEquals(settings.id, draft.id)
        assertEquals(settings.storeName, draft.storeName)
        assertEquals(settings.deliveryFee, draft.deliveryFee)
        assertEquals(settings.minOrderAmount, draft.minOrderAmount)
    }
}
