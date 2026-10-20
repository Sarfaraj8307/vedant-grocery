package com.vedant.admin.feature.auth

/**
 * Not-authorized state.
 *
 * Deliberately carries **no identity and no admin data** — the refusal screen
 * must not become a place where a non-admin can learn anything about the store's
 * operations.
 */
data class NotAuthorizedUiState(
    val isSigningOut: Boolean = false,
)
