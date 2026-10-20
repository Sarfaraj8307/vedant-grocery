package com.vedant.admin.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives session restore on the splash screen.
 *
 * It calls [AuthRepository.restoreSession], which loads the persisted session and
 * asks the server `is_admin()`. The resulting [com.vedant.admin.domain.model.SessionState]
 * flows through the session gate, which performs the actual navigation.
 *
 * A "no session" result ([AppError.SessionExpired] with nothing restored) is not
 * an error to display — the gate routes to sign-in. Only a *showable* failure
 * (e.g. the network) keeps the user on the splash with a retry.
 */
class SplashViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        restore()
    }

    fun restore() {
        _uiState.value = SplashUiState(isRestoring = true)
        viewModelScope.launch {
            when (val outcome = authRepository.restoreSession()) {
                is Outcome.Success ->
                    _uiState.value = SplashUiState(isRestoring = false)

                is Outcome.Failure ->
                    _uiState.value = SplashUiState(
                        isRestoring = false,
                        error = outcome.error.takeIf { it.isShowable() },
                    )
            }
        }
    }

    private fun AppError.isShowable(): Boolean = this != AppError.SessionExpired
}
