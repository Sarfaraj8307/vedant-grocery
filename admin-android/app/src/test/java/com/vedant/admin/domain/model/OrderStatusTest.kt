package com.vedant.admin.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order state machine is the highest-risk logic in the app: a wrong
 * transition moves a real customer's order to a state the shop did not intend,
 * and a wrong terminal check lets someone edit a delivered bill.
 *
 * These assertions are written against the *server's* rules
 * (`private.order_transition_allowed` in Postgres). If any of them fail, the
 * client and the server disagree and the server is right — fix the client.
 */
class OrderStatusTest {

    /** Every status reachable in the happy path, in order. */
    private val happyPath = listOf(
        OrderStatus.PENDING,
        OrderStatus.CONFIRMED,
        OrderStatus.PROCESSING,
        OrderStatus.READY_FOR_DELIVERY,
        OrderStatus.OUT_FOR_DELIVERY,
        OrderStatus.DELIVERED,
    )

    // --- The forward path ---------------------------------------------------

    @Test
    fun `happy path advances one step at a time to DELIVERED`() {
        happyPath.zipWithNext { from, to ->
            assertTrue(
                "expected $from -> $to to be offered",
                to in from.nextStatuses,
            )
        }
    }

    @Test
    fun `DELIVERED is reachable from OUT_FOR_DELIVERY`() {
        assertTrue(OrderStatus.DELIVERED in OrderStatus.OUT_FOR_DELIVERY.nextStatuses)
    }

    // --- Cancellation -------------------------------------------------------

    @Test
    fun `every non-terminal status may be cancelled`() {
        val nonTerminal = happyPath.filterNot { it.isTerminal }
        nonTerminal.forEach { status ->
            assertTrue(
                "expected $status to be cancellable",
                OrderStatus.CANCELLED in status.nextStatuses,
            )
        }
    }

    @Test
    fun `terminal statuses are final`() {
        assertTrue(OrderStatus.DELIVERED.isTerminal)
        assertTrue(OrderStatus.CANCELLED.isTerminal)
        assertEquals(emptyList<OrderStatus>(), OrderStatus.DELIVERED.nextStatuses)
        assertEquals(emptyList<OrderStatus>(), OrderStatus.CANCELLED.nextStatuses)
    }

    @Test
    fun `no non-terminal status is flagged terminal`() {
        happyPath.filterNot { it == OrderStatus.DELIVERED }.forEach {
            assertFalse("$it must not be terminal", it.isTerminal)
        }
    }

    // --- Illegal jumps must never be offered --------------------------------

    @Test
    fun `cannot skip stages`() {
        val illegalFromPending = listOf(
            OrderStatus.PROCESSING,
            OrderStatus.READY_FOR_DELIVERY,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED,
        )
        illegalFromPending.forEach {
            assertFalse(
                "PENDING must not offer $it directly",
                it in OrderStatus.PENDING.nextStatuses,
            )
        }
    }

    @Test
    fun `cannot move backwards`() {
        happyPath.drop(1).forEach { status ->
            val earlier = happyPath.takeWhile { it != status }
            earlier.forEach { previous ->
                assertFalse(
                    "$status must not offer a move back to $previous",
                    previous in status.nextStatuses,
                )
            }
        }
    }

    @Test
    fun `no status offers itself`() {
        OrderStatus.entries.forEach {
            assertFalse("$it must not offer itself", it in it.nextStatuses)
        }
    }

    @Test
    fun `no status offers an undefined status`() {
        // Guards against a future edit adding a status to `flow` but not to the
        // transition table.
        OrderStatus.entries.forEach { status ->
            status.nextStatuses.forEach { next ->
                assertTrue(
                    "$status offers $next which is not a defined status",
                    next in OrderStatus.entries,
                )
            }
        }
    }

    // --- Wire-format contract ----------------------------------------------

    @Test
    fun `wire names match the live Postgres enum exactly`() {
        // These strings are the database's. Renaming one silently breaks every
        // status read and write in the app.
        assertEquals(
            listOf(
                "PENDING",
                "CONFIRMED",
                "PROCESSING",
                "READY_FOR_DELIVERY",
                "OUT_FOR_DELIVERY",
                "DELIVERED",
                "CANCELLED",
            ),
            OrderStatus.entries.map { it.name },
        )
    }

    @Test
    fun `flow contains exactly the six forward stages`() {
        assertEquals(happyPath, OrderStatus.flow)
        assertFalse(OrderStatus.CANCELLED in OrderStatus.flow)
    }

    @Test
    fun `fromWire decodes every real value and tolerates junk`() {
        OrderStatus.entries.forEach {
            assertEquals(it, OrderStatus.fromWire(it.name))
        }
        assertEquals(OrderStatus.PENDING, OrderStatus.fromWire("pending"))
        assertNull(OrderStatus.fromWire("NOT_A_STATUS"))
        assertNull(OrderStatus.fromWire(null))
    }

    // --- Labels are wording, not identity -----------------------------------

    @Test
    fun `PENDING is presented as Order received not pending payment`() {
        // The DB value is PENDING, but it means "we have your order" - there is
        // no online payment in this business.
        assertEquals("Order received", OrderStatus.PENDING.label)
        assertEquals("Ready to dispatch", OrderStatus.READY_FOR_DELIVERY.label)
    }

    @Test
    fun `every status has a non-blank label`() {
        OrderStatus.entries.forEach {
            assertTrue("$it has no label", it.label.isNotBlank())
        }
    }
}