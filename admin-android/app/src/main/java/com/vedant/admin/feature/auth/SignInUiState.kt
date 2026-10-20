package com.vedant.admin.feature.auth

import com.vedant.admin.core.error.AppError

/**
 * Sign-in form state.
 *
 * Field-level validation is separate from the submit error ([error]): a blank
 * field is a local problem, while [error] is the server's (or the network's)
 * answer. Both are shown, but only [error] comes from the backend.
 */
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: AppError? = null,
) {
    /** The submit button is only enabled when it can actually do something. */
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}
