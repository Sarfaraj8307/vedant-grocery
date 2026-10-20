package com.vedant.admin.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The appearance mapping (§8): the persisted wire values, the system-defer
 * rule, and the tolerant decode. These are the rules the theme boundary relies
 * on, so they are pinned here rather than trusted.
 */
class AppearanceModeTest {

    @Test
    fun `system default defers to the system setting`() {
        assertTrue(AppearanceMode.SYSTEM_DEFAULT.prefersDark(systemInDark = true))
        assertFalse(AppearanceMode.SYSTEM_DEFAULT.prefersDark(systemInDark = false))
    }

    @Test
    fun `light and dark pin the answer regardless of the system`() {
        assertFalse(AppearanceMode.LIGHT.prefersDark(systemInDark = true))
        assertFalse(AppearanceMode.LIGHT.prefersDark(systemInDark = false))
        assertTrue(AppearanceMode.DARK.prefersDark(systemInDark = false))
        assertTrue(AppearanceMode.DARK.prefersDark(systemInDark = true))
    }

    @Test
    fun `every mode round-trips through its wire value`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromWire(mode.wire))
        }
    }

    @Test
    fun `the default is system default`() {
        assertEquals(AppearanceMode.SYSTEM_DEFAULT, AppearanceMode.DEFAULT)
    }

    @Test
    fun `a missing value decodes to the default`() {
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromWire(null))
    }

    @Test
    fun `an unknown value decodes to the default, not a guess`() {
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromWire("neon"))
        assertEquals(AppearanceMode.DEFAULT, AppearanceMode.fromWire(""))
    }

    @Test
    fun `the decode is trim and case tolerant`() {
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromWire("  DaRk "))
    }

    @Test
    fun `every mode has an operator-facing label`() {
        AppearanceMode.entries.forEach { mode ->
            assertTrue("${mode.name} has no label", mode.label.isNotBlank())
        }
    }
}
