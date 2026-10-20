package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.AdminIdentity
import com.vedant.admin.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication and the server-side admin gate.
 *
 * The single rule that governs this interface: **authorization is a server
 * question.** [isAdmin] and [restoreSession] call the `is_admin()` RPC; no method
 * here consults a locally cached role, a hidden button, or a client-side flag.
 */
interface AuthRepository {

    /** Mirrors the session: [SessionState.Unknown] until restore resolves. */
    val sessionState: StateFlow<SessionState>

    /** Email + password sign-in. On success the SDK emits the new session. */
    suspend fun signIn(email: String, password: String): Outcome<Unit>

    /** Signs out on this device and clears the local session. */
    suspend fun signOut(): Outcome<Unit>

    /**
     * Restores the persisted session (if any) and resolves the operator's
     * identity, including the **server-confirmed** admin status.
     *
     * Returns `Failure(AppError.SessionExpired)` when there is no session to
     * restore — the caller treats that as "signed out", not as an error to show.
     */
    suspend fun restoreSession(): Outcome<AdminIdentity>

    /** The server gate: `true` only when `is_admin()` says so. */
    suspend fun isAdmin(): Outcome<Boolean>

    /** The current user id — the first path segment of a `complaint-images` key. */
    suspend fun currentUserId(): String?

    /** A fresh access token for authenticated Storage uploads. */
    suspend fun currentAccessToken(): String?
}
