package com.vedant.admin.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.result.getOrNull
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The app-level session gate.
 *
 * It turns the raw auth session into the four states the navigation graph routes
 * on, and it is the **only** place the "is this user an admin?" question is
 * answered for routing. That answer always comes from the server
 * (`AuthRepository.isAdmin()` → the `is_admin()` RPC); a locally cached role is
 * never consulted (CLAUDE.md rule 2).
 *
 * [resolve] is a pure function so the mapping can be unit-tested without a
 * coroutine scope or a repository.
 */
class SessionViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { raw ->
                val isAdmin = if (raw is SessionState.SignedIn) {
                    // Fail-closed: an unconfirmed or failed check is NOT an admin.
                    authRepository.isAdmin().getOrNull() == true
                } else {
                    false
                }
                _sessionState.value = resolve(raw, isAdmin)
            }
        }
    }

    /** Signs out on this device; the session flow then drives the gate to sign-in. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        /**
         * Maps the raw session to the app-level gate state.
         *
         * A signed-in user becomes [SessionState.SignedIn] only when the server
         * confirmed admin; otherwise the gate refuses with
         * [SessionState.NotAuthorized] and no admin data is shown.
         */
        fun resolve(raw: SessionState, isAdmin: Boolean): SessionState = when (raw) {
            is SessionState.SignedIn -> if (isAdmin) raw else SessionState.NotAuthorized
            else -> raw
        }
    }
}
