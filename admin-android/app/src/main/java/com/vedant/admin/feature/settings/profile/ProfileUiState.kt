package com.vedant.admin.feature.settings.profile

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.domain.model.OperatorProfile
import com.vedant.admin.ui.components.ContentPhase

/**
 * Immutable state for the profile screen.
 *
 * [profile] carries the **server-resolved** role ([OperatorProfile.isAdmin]); the
 * screen never renders a locally cached one. When the session is gone,
 * [sessionExpired] is true so the screen can say so plainly and point back to
 * sign-in rather than showing a stale identity.
 */
data class ProfileUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val loadError: AppError? = null,
    val profile: OperatorProfile? = null,
    val isSigningOut: Boolean = false,
    val signOutError: AppError? = null,
    /** The persisted appearance choice, shown on the device settings entry (§8). */
    val appearanceMode: AppearanceMode = AppearanceMode.DEFAULT,
) {

    val sessionExpired: Boolean get() = loadError == AppError.SessionExpired
}
