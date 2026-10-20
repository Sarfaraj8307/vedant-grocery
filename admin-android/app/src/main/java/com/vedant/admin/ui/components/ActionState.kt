package com.vedant.admin.ui.components

import com.vedant.admin.core.error.AppError

/**
 * The state of a single mutating action (§13): `Idle → InFlight → Idle | Failed`.
 *
 * Shared by every screen that triggers a write, so "an in-flight action blocks a
 * double-submit" and "a failure leaves the rendered data untouched" are the same
 * mechanism everywhere rather than re-implemented per screen.
 *
 * [Failed] carries the mapped [AppError]; the screen shows its copy and leaves
 * the rendered data unchanged — the UI never moves on a rejected action.
 */
sealed interface ActionState {
    data object Idle : ActionState
    data object InFlight : ActionState
    data class Failed(val error: AppError) : ActionState
}
