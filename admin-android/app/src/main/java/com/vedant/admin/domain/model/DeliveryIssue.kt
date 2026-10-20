package com.vedant.admin.domain.model

/**
 * The escalation taxonomy for the courier's REPORT ISSUE action (§25).
 *
 * The `wire` values are the exact strings the server's CHECK constraint
 * accepts (migration `20261016_016_delivery_issues.sql`) — a `type` that does
 * not round-trip is rejected server-side, so these two lists are kept side by
 * side and this enum is the client's only source for them.
 */
enum class DeliveryIssueType(
    /** The server-side value, persisted verbatim. */
    val wire: String,
    /** The operator-facing label. */
    val label: String,
) {
    /** The customer did not come to the door. */
    CUSTOMER_UNAVAILABLE("customer_unavailable", "Customer unavailable"),

    /** The address is wrong, incomplete or unreachable. */
    ADDRESS_ISSUE("address_issue", "Address issue"),

    /** The phone rings out or is unreachable. */
    PHONE_UNREACHABLE("phone_unreachable", "Phone unreachable"),

    /** A payment problem (cash, change, online failure). */
    PAYMENT_ISSUE("payment_issue", "Payment issue"),

    /** Goods arrived at the door damaged. */
    DAMAGED_ITEM("damaged_item", "Damaged item"),

    /** Anything else — the reason field carries the specifics. */
    OTHER("other", "Other"),
    ;

    companion object {
        /** The value the dialog preselects. */
        val DEFAULT: DeliveryIssueType = CUSTOMER_UNAVAILABLE

        /**
         * Decodes a server value. An unknown value (a newer server taxonomy)
         * degrades to [OTHER] rather than throwing — the reason text is what
         * the admin reads first.
         */
        fun fromWire(raw: String?): DeliveryIssueType {
            val normalized = raw?.trim()?.lowercase() ?: return DEFAULT
            return entries.firstOrNull { it.wire == normalized } ?: OTHER
        }
    }
}

/**
 * The client-side half of "a reason is required for every type" (§25).
 *
 * The server enforces the same rule (`NOT NULL` + a non-blank, ≤ 500-char
 * CHECK in migration 016), but duplicating it here means the dialog can
 * disable its submit button and explain the problem **before** a request is
 * wasted — and it makes the rule unit-testable without a device or a network.
 *
 * This object is the pure rule; the ViewModel owns the in-flight state.
 */
object DeliveryIssueReport {

    /** Matches the server's CHECK upper bound — keep the two in step. */
    const val MAX_REASON_LENGTH = 500

    /**
     * Validates and normalises a reason.
     *
     * @return the trimmed reason when it is usable, or `null` when it is
     *   blank or over [MAX_REASON_LENGTH]. The trimmed form is what both the
     *   dialog and the server see, so a check can never pass on whitespace
     *   alone.
     */
    fun validateReason(reason: String): String? {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > MAX_REASON_LENGTH) return null
        return trimmed
    }
}
