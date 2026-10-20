package com.vedant.admin.domain.model

/**
 * The app-level authentication + authorization state.
 *
 * [SignedIn] means *an admin*: the server-confirmed result of `private.is_admin()`.
 * There is deliberately no "signed in but role unknown" member — a session whose
 * admin status has not been confirmed is [Unknown], never [SignedIn] (CLAUDE.md
 * rule 2: UI visibility is never authorization).
 */
sealed interface SessionState {

    /** Session restore in flight — the app shows a spinner and nothing else. */
    data object Unknown : SessionState

    /** No session. Route to sign-in. */
    data object SignedOut : SessionState

    /** A server-confirmed admin. Route to the dashboard. */
    data class SignedIn(val userId: String, val email: String?) : SessionState

    /** Authenticated, but the server says this user is not an admin. */
    data object NotAuthorized : SessionState
}
