package com.vedant.admin.feature.auth

import com.vedant.admin.core.error.AppError

/**
 * Splash state.
 *
 * The splash only reports whether session restore is still running and, if it
 * failed in a way worth showing, the error. Routing itself is not here — the
 * session gate ([com.vedant.admin.ui.navigation.SessionViewModel]) decides the
 * destination from the resolved [com.vedant.admin.domain.model.SessionState].
 */
data class SplashUiState(
    val isRestoring: Boolean = true,
    /** Non-null only for a *showable* failure (e.g. network); "no session" is not one. */
    val error: AppError? = null,
)
