package com.vedant.admin.data.session

import com.vedant.admin.domain.model.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the raw authentication session: the persisted token, the current user id
 * and email, and a [sessionState] flow that mirrors the SDK.
 *
 * This is an **interface** so the repository above it can be unit-tested with a
 * fake, without an SDK client or a network. Only [SupabaseSessionStore] imports
 * `io.github.jan.*`.
 *
 * Note: this type intentionally does **not** decide admin status. Whether a
 * signed-in user is an admin is a server question answered by the `is_admin()`
 * RPC, which lives in `AdminRpc`.
 */
interface SessionStore {

    /** Mirrors the SDK session: [SessionState.Unknown] until restore completes. */
    val sessionState: StateFlow<SessionState>

    /** The current user's id, or `null` when signed out. */
    fun currentUserId(): String?

    /** The current user's email, or `null`. */
    fun currentEmail(): String?

    /** A fresh access token for authenticated Storage uploads, or `null`. */
    fun currentAccessToken(): String?

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()

    /** Loads the persisted session, if any. Returns true when a session was restored. */
    suspend fun loadFromStorage(): Boolean
}

/**
 * The real implementation, backed by supabase-kt's GoTrue auth and a
 * `SettingsSessionManager` (see `SupabaseProvider`) so the session survives
 * process death.
 */
class SupabaseSessionStore(private val client: SupabaseClient) : SessionStore {

    private val auth get() = client.auth

    /**
     * App-scoped: lives as long as the process. `Eagerly` starts the mapping
     * immediately so the first collector never sees a stale initial value.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val sessionState: StateFlow<SessionState> =
        auth.sessionStatus
            .map(::toSessionState)
            .stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

    override fun currentUserId(): String? = auth.currentUserOrNull()?.id

    override fun currentEmail(): String? = auth.currentUserOrNull()?.email

    override fun currentAccessToken(): String? = auth.currentAccessTokenOrNull()

    override suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        // LOCAL: this device only. Other sessions of the same user stay signed in.
        auth.signOut(SignOutScope.LOCAL)
    }

    override suspend fun loadFromStorage(): Boolean = auth.loadFromStorage(true) ?: false

    private fun toSessionState(status: SessionStatus): SessionState = when (status) {
        is SessionStatus.Initializing -> SessionState.Unknown
        is SessionStatus.Authenticated -> {
            val user = status.session.user
            SessionState.SignedIn(userId = user?.id.orEmpty(), email = user?.email)
        }
        is SessionStatus.NotAuthenticated -> SessionState.SignedOut
        is SessionStatus.RefreshFailure -> SessionState.SignedOut
    }
}
