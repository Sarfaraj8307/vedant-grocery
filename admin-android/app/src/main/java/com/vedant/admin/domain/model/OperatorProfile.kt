package com.vedant.admin.domain.model

/**
 * The signed-in operator, as shown on the profile screen.
 *
 * ### The role is the server's answer
 * [isAdmin] is **not** read from the `profiles.role` column and is **not** a
 * cached local flag — it is the value returned by the `is_admin()` RPC for the
 * current session (`docs/CONTRACT.md` §5; `CLAUDE.md` rule 2). [storedRole] is
 * the raw column, shown only as informational metadata and explicitly **not** the
 * source of any authorization decision.
 */
data class OperatorProfile(
    val userId: String,
    val email: String?,
    val displayName: String,
    val username: String?,
    val phone: String?,
    val storedRole: String?,
    val isAdmin: Boolean,
) {

    /** The role wording shown on screen, derived from the server's gate. */
    val roleLabel: String get() = if (isAdmin) "Administrator" else "Not an administrator"
}
