package com.vedant.admin.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the sign-in form.
 *
 * Two rules are enforced here, not in the composable:
 *  - **Double-submit is blocked**: [submit] returns immediately while a request
 *    is already in flight, and the button is disabled in that state.
 *  - **No fake success**: the screen never navigates itself. On success the SDK
 *    emits a new session, the session gate re-evaluates the admin status against
 *    the server, and *that* decides the destination.
 */
class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val emailError = if (current.email.isBlank()) "Enter your email address." else null
        val passwordError = if (current.password.isEmpty()) "Enter your password." else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.signIn(current.email, current.password)) {
                is Outcome.Success ->
                    // Clear the password from memory; the gate handles navigation.
                    _uiState.update { it.copy(isSubmitting = false, password = "") }

                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }
}
