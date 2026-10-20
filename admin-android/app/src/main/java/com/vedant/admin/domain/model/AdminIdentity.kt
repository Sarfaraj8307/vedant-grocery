package com.vedant.admin.domain.model

/**
 * The identity of the signed-in operator, as resolved from the **server**.
 *
 * [isAdmin] is never a locally-cached role: it is the value returned by the
 * `is_admin()` RPC for the current session. If it is `false`, the app shows the
 * refusal screen and exposes no admin data.
 */
data class AdminIdentity(
    val userId: String,
    val email: String?,
    val isAdmin: Boolean,
)
