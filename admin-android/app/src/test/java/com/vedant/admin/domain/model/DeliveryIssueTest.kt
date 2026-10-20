package com.vedant.admin.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The REPORT ISSUE taxonomy and the "a reason is required for every type" rule
 * (§25) — the client half of the contract migration 016 enforces server-side.
 */
class DeliveryIssueTest {

    // --- Taxonomy -------------------------------------------------------------

    @Test
    fun `every issue type has a stable wire value matching the server check`() {
        // Keep in step with the CHECK constraint in
        // 20261016_016_delivery_issues.sql — a drift here is a refused insert.
        val expected = setOf(
            "customer_unavailable",
            "address_issue",
            "phone_unreachable",
            "payment_issue",
            "damaged_item",
            "other",
        )
        assertEquals(expected, DeliveryIssueType.entries.map { it.wire }.toSet())
    }

    @Test
    fun `every issue type has an operator-facing label`() {
        DeliveryIssueType.entries.forEach { type ->
            assert(type.label.isNotBlank()) { "${type.name} has no label" }
        }
    }

    @Test
    fun `every wire value round-trips`() {
        DeliveryIssueType.entries.forEach { type ->
            assertEquals(type, DeliveryIssueType.fromWire(type.wire))
        }
    }

    @Test
    fun `an unknown wire value degrades to other`() {
        assertEquals(DeliveryIssueType.OTHER, DeliveryIssueType.fromWire("meteor_strike"))
    }

    @Test
    fun `the dialog preselects customer unavailable`() {
        assertEquals(DeliveryIssueType.CUSTOMER_UNAVAILABLE, DeliveryIssueType.DEFAULT)
    }

    // --- Reason validation ------------------------------------------------------

    @Test
    fun `a reason is required - blank and whitespace-only are refused`() {
        assertNull(DeliveryIssueReport.validateReason(""))
        assertNull(DeliveryIssueReport.validateReason("   \n\t "))
    }

    @Test
    fun `a usable reason is returned trimmed`() {
        assertEquals("Door code 4471", DeliveryIssueReport.validateReason("  Door code 4471 "))
    }

    @Test
    fun `a reason at the length limit is accepted, past it is refused`() {
        val atLimit = "x".repeat(DeliveryIssueReport.MAX_REASON_LENGTH)
        val pastLimit = "x".repeat(DeliveryIssueReport.MAX_REASON_LENGTH + 1)
        assertEquals(DeliveryIssueReport.MAX_REASON_LENGTH, DeliveryIssueReport.validateReason(atLimit)?.length)
        assertNull(DeliveryIssueReport.validateReason(pastLimit))
    }

    @Test
    fun `the length limit matches the server check constraint`() {
        assertEquals(500, DeliveryIssueReport.MAX_REASON_LENGTH)
    }
}
