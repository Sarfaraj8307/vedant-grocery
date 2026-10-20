package com.vedant.admin.domain.usecase

import com.vedant.admin.domain.model.OrderStatus

/**
 * The order state machine, as the client understands it.
 *
 * **This is a convenience, not an authority.** The real gate is
 * `private.order_transition_allowed(from, to)` inside the Postgres function
 * `admin_update_order_status`; it re-validates every transition server-side. The
 * UI uses this table only to decide which buttons to *offer*. If the two ever
 * disagree, the server wins and the app surfaces the rejection — it never
 * assumes the move succeeded (§5.2, CLAUDE.md rule 2).
 *
 * It is a pure object so it can be asserted directly without a coroutine scope,
 * a repository, or a device.
 */
object OrderTransitions {

    /**
     * The statuses that may be offered as a next step from [from].
     *
     * A terminal status (or an unknown/absent one) offers nothing.
     */
    fun nextStatuses(from: OrderStatus?): List<OrderStatus> = from?.nextStatuses.orEmpty()

    /** True when [to] is one of the transitions the UI is willing to offer. */
    fun canTransition(from: OrderStatus?, to: OrderStatus): Boolean =
        from != null && to in from.nextStatuses

    /** True when the target status is terminal and therefore requires a reason. */
    fun requiresReason(to: OrderStatus): Boolean = to == OrderStatus.CANCELLED

    /** True for a status no transition may leave. */
    fun isTerminal(status: OrderStatus?): Boolean = status?.isTerminal == true

    /**
     * Validates the reason for a move the UI is about to request. Returns a
     * human-readable problem, or `null` when the input is acceptable.
     *
     * The server enforces this too; checking locally just avoids a pointless
     * round-trip and gives the operator an immediate message.
     */
    fun reasonProblem(to: OrderStatus, reason: String?): String? =
        if (requiresReason(to) && reason.isNullOrBlank()) {
            "A cancellation reason is required."
        } else {
            null
        }
}
