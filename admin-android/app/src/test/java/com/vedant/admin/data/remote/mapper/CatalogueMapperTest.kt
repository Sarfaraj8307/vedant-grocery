package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.CategoryDto
import com.vedant.admin.data.remote.dto.OfferDto
import com.vedant.admin.data.remote.dto.ProductDto
import com.vedant.admin.data.remote.dto.ProductWeightDto
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.model.ProductDraft
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T18 acceptance — the §10 legacy/duplicate policy, read and write.
 *
 * Proves, for every catalogue pair, that the mapper reads the authoritative
 * column, falls back to the legacy one, and — on write — emits **both** so the
 * ambiguity can never bite.
 */
class CatalogueMapperTest {

    // --- Pair 1: base_price / price ----------------------------------------

    @Test
    fun `base_price is read when present`() {
        val product = ProductDto(basePrice = Money(7200), price = Money(9999)).toDomain()
        assertEquals(Money(7200), product.basePrice)
    }

    @Test
    fun `price is the fallback when base_price is absent`() {
        val product = ProductDto(price = Money(7200)).toDomain()
        assertEquals(Money(7200), product.basePrice)
    }

    // --- Pair 2: quantity_limit / max_order_qty ----------------------------

    @Test
    fun `quantity_limit is read when present`() {
        val product = ProductDto(quantityLimit = 5, maxOrderQty = 9).toDomain()
        assertEquals(5, product.quantityLimit)
    }

    @Test
    fun `max_order_qty is the fallback when quantity_limit is absent`() {
        val product = ProductDto(maxOrderQty = 5).toDomain()
        assertEquals(5, product.quantityLimit)
    }

    // --- availability is a boolean (live evidence) -------------------------

    @Test
    fun `availability decodes as a boolean and defaults to true when absent`() {
        assertTrue(ProductDto(availability = false).toDomain().availability.not())
        assertTrue(ProductDto().toDomain().availability)
    }

    // --- Write-both --------------------------------------------------------

    @Test
    fun `toWriteFields sets both price and base_price to the same value`() {
        val fields = draft(basePrice = Money(7200)).toWriteFields()
        assertEquals(Money(7200), fields.basePrice)
        assertEquals(Money(7200), fields.price)
    }

    @Test
    fun `toWriteFields sets both quantity_limit and max_order_qty equal`() {
        val fields = draft(quantityLimit = 5).toWriteFields()
        assertEquals(5, fields.quantityLimit)
        assertEquals(5, fields.maxOrderQty)
    }

    @Test
    fun `toWriteFields derives a slug when none is supplied`() {
        val fields = draft(name = "Fresh Tomatoes!").toWriteFields()
        assertEquals("fresh-tomatoes", fields.slug)
    }

    @Test
    fun `a create draft omits the id so the server assigns one`() {
        assertNull(draft(id = null).toWriteFields().id)
    }

    // --- Pair 5: glyph / accent / icon -------------------------------------

    @Test
    fun `category glyph is read and icon is only the fallback`() {
        assertEquals("🍎", CategoryDto(glyph = "🍎", icon = "🥬").toDomain().glyph)
        assertEquals("🥕", CategoryDto(icon = "🥕").toDomain().glyph)
    }

    @Test
    fun `category write mirrors icon from glyph, never the reverse`() {
        val fields = CategoryDraft(name = "Fruits", glyph = "🍎", accent = 16711680).toWriteFields()
        assertEquals("🍎", fields.glyph)
        assertEquals("🍎", fields.icon)
        assertEquals(16711680, fields.accent)
        assertEquals("fruits", fields.slug)
    }

    // --- Weight / offer ----------------------------------------------------

    @Test
    fun `weight price maps one to one and grams are kept`() {
        val weight = ProductWeightDto(id = "w1", productId = "p1", label = "1 kg", grams = 1000, price = Money(4000)).toDomain()
        assertEquals(Money(4000), weight.price)
        assertEquals(1000, weight.grams)
        assertTrue(weight.isAvailable)
    }

    @Test
    fun `offer timestamps parse and a malformed one becomes null`() {
        val offer = OfferDto(
            id = "of1",
            productId = "p1",
            title = "Diwali",
            discountPercent = 20,
            startsAt = "2026-10-11T00:00:00Z",
            endsAt = "not-a-date",
        ).toDomain()
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), offer.startsAt)
        assertNull(offer.endsAt)
        assertEquals("20% off", offer.valueLabel)
    }

    @Test
    fun `a discount lowers the effective price and reports a percentage`() {
        val product = ProductDto(basePrice = Money(10000), discountPrice = Money(7500)).toDomain()
        assertTrue(product.hasDiscount)
        assertEquals(Money(7500), product.effectivePrice)
        assertEquals(25, product.discountPercent)
    }

    private fun draft(
        id: String? = "p1",
        name: String = "Tomatoes",
        basePrice: Money = Money(7200),
        quantityLimit: Int? = 5,
    ) = ProductDraft(id = id, name = name, basePrice = basePrice, quantityLimit = quantityLimit)
}
