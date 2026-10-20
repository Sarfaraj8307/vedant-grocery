package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.domain.model.StoreSettingsDraft

/**
 * The store's single-row configuration.
 *
 * ### Never fake success
 * [saveSettings] returns the **server-confirmed row** PostgREST echoed, never the
 * draft that was sent. On a rejection the outcome is `Failure` and the UI keeps
 * the row it already had.
 *
 * ### Not cached, not queued
 * A settings write is an authoritative change to what customers see, so it is
 * **never** written to the offline cache as a success and never queued for later:
 * if the server cannot be reached the save is disabled and the operator is told.
 */
interface StoreSettingsRepository {

    /** Reads the single settings row. */
    suspend fun getSettings(): Outcome<StoreSettings>

    /** Writes the editable subset and returns the stored row. */
    suspend fun saveSettings(draft: StoreSettingsDraft): Outcome<StoreSettings>
}
