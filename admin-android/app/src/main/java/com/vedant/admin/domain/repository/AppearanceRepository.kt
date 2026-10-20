package com.vedant.admin.domain.repository

import com.vedant.admin.domain.model.AppearanceMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The operator's persisted appearance choice (§8).
 *
 * A repository — not a raw preference read — so the ViewModel layer never sees
 * the persistence mechanism (the same rule every other setting follows), and
 * so the choice can be faked in tests.
 */
interface AppearanceRepository {

    /**
     * The persisted choice.
     *
     * A [StateFlow] seeded **synchronously** from storage on first collection,
     * so the very first frame of the app already uses the right theme — there
     * is no light-flash-then-dark flash on a cold start for a dark-mode user.
     */
    val mode: StateFlow<AppearanceMode>

    /** Persists [mode]. Takes effect on the next composition of the theme. */
    suspend fun setMode(mode: AppearanceMode)
}
