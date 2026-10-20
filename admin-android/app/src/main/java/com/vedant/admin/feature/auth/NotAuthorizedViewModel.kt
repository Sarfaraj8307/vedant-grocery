package com.vedant.admin.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the refusal screen. Its only action is to sign out, which clears the
 * session and lets the gate route back to sign-in.
 */
class NotAuthorizedViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NotAuthorizedUiState())
    val uiState: StateFlow<NotAuthorizedUiState> = _uiState.asStateFlow()

    fun signOut() {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(isSigningOut = false) }
        }
    }
}
