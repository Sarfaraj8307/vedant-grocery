package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorMapper
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.session.SessionStore
import com.vedant.admin.domain.model.AdminIdentity
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * The real [AuthRepository].
 *
 * It composes two collaborators that are both replaceable in tests:
 *  - [SessionStore] — the raw session (token, user id, email).
 *  - [AdminRpc]     — the server authorization gate (`is_admin()`).
 *
 * The admin decision is always the server's. [restoreSession] loads the session
 * and then asks `is_admin()`; the resulting [AdminIdentity.isAdmin] is what the
 * UI routes on. Nothing here reads a local role.
 */
class AuthRepositoryImpl(
    private val sessionStore: SessionStore,
    private val adminRpc: AdminRpc,
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepository"
    }

    override val sessionState: StateFlow<SessionState> = sessionStore.sessionState

    override suspend fun signIn(email: String, password: String): Outcome<Unit> =
        outcomeOf {
            AppLog.i(TAG, "signIn attempt")
            sessionStore.signIn(email.trim(), password)
        }

    override suspend fun signOut(): Outcome<Unit> = outcomeOf {
        AppLog.i(TAG, "signOut")
        sessionStore.signOut()
    }

    override suspend fun restoreSession(): Outcome<AdminIdentity> = try {
        sessionStore.loadFromStorage()
        val userId = sessionStore.currentUserId()
        if (userId == null) {
            // No session to restore. Not an error to show — the caller routes to
            // the sign-in screen. SessionExpired is the closest taxonomy member.
            AppLog.i(TAG, "restoreSession: no persisted session")
            Outcome.Failure(AppError.SessionExpired)
        } else {
            // The one authorization question, answered by the server.
            val isAdmin = adminRpc.isAdmin()
            AppLog.i(TAG, "restoreSession: resolved admin=$isAdmin")
            Outcome.Success(
                AdminIdentity(
                    userId = userId,
                    email = sessionStore.currentEmail(),
                    isAdmin = isAdmin,
                ),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppLog.w(TAG, "restoreSession failed", throwable)
        Outcome.Failure(ErrorMapper.map(throwable))
    }

    override suspend fun isAdmin(): Outcome<Boolean> = outcomeOf { adminRpc.isAdmin() }

    override suspend fun currentUserId(): String? = sessionStore.currentUserId()

    override suspend fun currentAccessToken(): String? = sessionStore.currentAccessToken()
}
