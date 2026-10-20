package com.vedant.admin.domain.usecase

import com.vedant.admin.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T12 acceptance: terminal states have no next states; `CANCELLED` is reachable
 * from every non-terminal state; an unknown wire value decodes to `null` rather
 * than throwing.
 */
class OrderStateMachineTest {

    private val nonTerminal = listOf(
        OrderStatus.PENDING,
        OrderStatus.CONFIRMED,
        OrderStatus.PROCESSING,
        OrderStatus.READY_FOR_DELIVERY,
        OrderStatus.OUT_FOR_DELIVERY,
    )

    @Test
    fun `terminal states have no next states`() {
        assertTrue(OrderTransitions.nextStatuses(OrderStatus.DELIVERED).isEmpty())
        assertTrue(OrderTransitions.nextStatuses(OrderStatus.CANCELLED).isEmpty())
        assertTrue(OrderTransitions.isTerminal(OrderStatus.DELIVERED))
        assertTrue(OrderTransitions.isTerminal(OrderStatus.CANCELLED))
    }

    @Test
    fun `cancelled is reachable from every non-terminal state`() {
        nonTerminal.forEach { from ->
            assertTrue(
                "CANCELLED must be reachable from $from",
                OrderTransitions.canTransition(from, OrderStatus.CANCELLED),
            )
        }
    }

    @Test
    fun `non-terminal states are not terminal`() {
        nonTerminal.forEach { assertFalse(OrderTransitions.isTerminal(it)) }
    }

    @Test
    fun `a null status offers nothing and permits nothing`() {
        assertTrue(OrderTransitions.nextStatuses(null).isEmpty())
        assertFalse(OrderTransitions.canTransition(null, OrderStatus.CONFIRMED))
        assertFalse(OrderTransitions.isTerminal(null))
    }

    @Test
    fun `the happy path advances one step at a time`() {
        assertEquals(listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED), OrderTransitions.nextStatuses(OrderStatus.PENDING))
        assertTrue(OrderTransitions.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED))
        assertFalse(OrderTransitions.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED))
    }

    @Test
    fun `cancelling requires a reason`() {
        assertTrue(OrderTransitions.requiresReason(OrderStatus.CANCELLED))
        assertFalse(OrderTransitions.requiresReason(OrderStatus.CONFIRMED))
        assertEquals(
            "A cancellation reason is required.",
            OrderTransitions.reasonProblem(OrderStatus.CANCELLED, "   "),
        )
        assertNull(OrderTransitions.reasonProblem(OrderStatus.CANCELLED, "customer called"))
        assertNull(OrderTransitions.reasonProblem(OrderStatus.CONFIRMED, null))
    }

    @Test
    fun `an unknown wire value decodes to null without throwing`() {
        assertNull(OrderStatus.fromWire("NOT_A_STATUS"))
        assertNull(OrderStatus.fromWire(null))
        assertNull(OrderStatus.fromWire(""))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromWire("PENDING"))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromWire("pending"))
    }

    @Test
    fun `retired status names are not recognised`() {
        listOf("ORDER_RECEIVED", "OWNER_ACCEPTED", "PREPARING", "READY", "IN_REVIEW").forEach {
            assertNull("'$it' must not decode", OrderStatus.fromWire(it))
        }
    }
}
