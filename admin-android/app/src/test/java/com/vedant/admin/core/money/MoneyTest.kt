package com.vedant.admin.core.money

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T04 acceptance: money is an exact integer-paise value that parses the formats
 * PostgREST emits (`152`, `152.0`, `152.00`, `-132.50`) without ever touching
 * `Double`, and renders with Indian grouping.
 */
class MoneyTest {

    @Test
    fun `parses an integer with no decimals as whole rupees`() {
        assertEquals(15_200L, Money.parse("152").paise)
    }

    @Test
    fun `parses one decimal place`() {
        assertEquals(15_200L, Money.parse("152.0").paise)
    }

    @Test
    fun `parses a two-decimal value`() {
        assertEquals(15_200L, Money.parse("152.00").paise)
    }

    @Test
    fun `parses a negative amount`() {
        assertEquals(-13_250L, Money.parse("-132.50").paise)
    }

    @Test
    fun `parses a leading plus sign`() {
        assertEquals(13_250L, Money.parse("+132.50").paise)
    }

    @Test
    fun `rounds the third decimal half up`() {
        assertEquals(15_213L, Money.parse("152.125").paise)
    }

    @Test
    fun `parses an indian grouped currency string`() {
        assertEquals(123_450L, Money.parse("₹1,234.50").paise)
    }

    @Test
    fun `rejects blank input`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("   ") }
    }

    @Test
    fun `rejects non-numeric input`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("12a") }
    }

    @Test
    fun `rejects two decimal points`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.2.3") }
    }

    @Test
    fun `formats with indian grouping and two decimals`() {
        assertEquals("₹1,234.50", Money(123_450).format())
    }

    @Test
    fun `formats a negative amount`() {
        assertEquals("-₹132.50", Money(-13_250).format())
    }

    @Test
    fun `formats zero`() {
        assertEquals("₹0.00", Money.ZERO.format())
    }

    @Test
    fun `arithmetic is exact`() {
        assertEquals(Money(30_000), Money(15_000) + Money(15_000))
        assertEquals(Money(0), Money(15_000) - Money(15_000))
        assertEquals(Money(-15_000), -Money(15_000))
    }

    @Test
    fun `comparison orders by paise`() {
        assertTrue(Money(100) < Money(200))
        assertTrue(Money(-1) < Money.ZERO)
    }

    @Test
    fun `isZero and isNegative`() {
        assertTrue(Money.ZERO.isZero)
        assertFalse(Money(1).isZero)
        assertTrue(Money(-1).isNegative)
        assertFalse(Money(1).isNegative)
    }

    @Test
    fun `ofRupees multiplies by one hundred`() {
        assertEquals(Money(15_000), Money.ofRupees(150))
    }

    @Test
    fun `toPlainString is a machine parseable decimal without symbol or grouping`() {
        assertEquals("152.00", Money(15_200).toPlainString())
        assertEquals("-132.50", Money(-13_250).toPlainString())
        assertEquals("0.00", Money.ZERO.toPlainString())
    }

    @Test
    fun `toPlainString round trips through parse`() {
        val original = Money(1_234_567)
        assertEquals(original, Money.parse(original.toPlainString()))
    }

    @Test
    fun `serializer writes a plain decimal rupee string`() {
        // A JSON string primitive is quoted, so the encoded form carries quotes.
        assertEquals("\"152.00\"", Json.encodeToString(MoneySerializer, Money(15_200)))
    }

    @Test
    fun `serializer round trips`() {
        val encoded = Json.encodeToString(MoneySerializer, Money(15_200))
        assertEquals(Money(15_200), Json.decodeFromString(MoneySerializer, encoded))
    }

    @Test
    fun `serializer reads a quoted decimal string emitted by postgrest`() {
        assertEquals(Money(15_250), Json.decodeFromString(MoneySerializer, "\"152.50\""))
    }

    // --- Rounding boundaries (the third decimal is the only rounded digit) ---

    @Test
    fun `a third decimal of four rounds down`() {
        assertEquals(15_212L, Money.parse("152.124").paise)
    }

    @Test
    fun `a third decimal of exactly five rounds half up`() {
        assertEquals(15_213L, Money.parse("152.125").paise)
    }

    @Test
    fun `a third decimal of nine rounds up and carries`() {
        assertEquals(15_213L, Money.parse("152.129").paise)
    }

    @Test
    fun `half a paisa rounds up to one paisa`() {
        assertEquals(1L, Money.parse("0.005").paise)
    }

    @Test
    fun `a third decimal below five rounds a sub-paisa value to zero`() {
        assertEquals(0L, Money.parse("0.004").paise)
    }

    @Test
    fun `rounding carries across the rupee boundary`() {
        // 0.999 -> 0 rupees + 0.999 paise-ish -> 100 paise -> 1 rupee exactly.
        assertEquals(100L, Money.parse("0.999").paise)
    }

    @Test
    fun `rounding carries from nine point nine nine nine to ten rupees`() {
        assertEquals(1_000L, Money.parse("9.999").paise)
    }

    @Test
    fun `a negative third decimal rounds away from zero`() {
        assertEquals(-15_213L, Money.parse("-152.125").paise)
    }

    @Test
    fun `a large amount rounds without overflowing paise`() {
        assertEquals(100_000_000_000L, Money.parse("999999999.999").paise)
    }

    @Test
    fun `parses a grouped amount with a leading rupee symbol and no decimals`() {
        assertEquals(12_345_678_900L, Money.parse("₹12,34,56,789").paise)
    }

    @Test
    fun `formats a large amount with full indian grouping`() {
        assertEquals("₹12,34,56,789.00", Money(12_345_678_900L).format())
    }

    @Test
    fun `formats a crore-scale amount in indian grouping`() {
        assertEquals("₹1,23,45,678.90", Money(1_234_567_890L).format())
    }

    @Test
    fun `toPlainString of a large amount has no grouping`() {
        assertEquals("12345678.90", Money(1_234_567_890L).toPlainString())
    }

    @Test
    fun `parse rejects a lone sign with no digits`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("-") }
    }

    @Test
    fun `parse rejects a lone decimal point`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse(".") }
    }

    @Test
    fun `parse treats a bare fraction as a sub-rupee amount`() {
        assertEquals(50L, Money.parse(".50").paise)
    }
}
