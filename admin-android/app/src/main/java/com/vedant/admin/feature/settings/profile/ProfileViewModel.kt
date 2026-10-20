package com.vedant.admin.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.repository.AppearanceRepository
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.ProfileRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the profile screen.
 *
 * The role shown is whatever [ProfileRepository] resolves from the server's
 * `is_admin()` — this ViewModel has no local role to fall back on. Sign-out clears
 * the session via [AuthRepository]; the navigation gate observes the resulting
 * session change and routes back to sign-in, so the screen itself never has to
 * fake a transition.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    appearanceRepository: AppearanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // The appearance choice is a device setting, so it rides along on this
    // screen's state — the entry row shows the live value, and a change made
    // on the appearance screen is reflected here the moment it is persisted.
    init {
        viewModelScope.launch {
            appearanceRepository.mode.collect { mode ->
                _uiState.update { it.copy(appearanceMode = mode) }
            }
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun reload() = load()

    /** Signs out on this device. The nav gate handles the routing on success. */
    fun signOut() {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true, signOutError = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.signOut()) {
                is Outcome.Success -> _uiState.update { it.copy(isSigningOut = false) }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isSigningOut = false, signOutError = outcome.error)
                }
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = profileRepository.getProfile()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(phase = ContentPhase.Data, profile = outcome.value, loadError = null)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = outcome.error)
                }
            }
        }
    }
}
