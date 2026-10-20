package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.SettingsRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.domain.model.StoreSettingsDraft
import com.vedant.admin.domain.repository.StoreSettingsRepository

private const val TAG = "StoreSettingsRepo"

/**
 * The real [StoreSettingsRepository].
 *
 * A thin, honest layer over [SettingsRemoteSource]: the write returns whatever
 * PostgREST echoed, and a rejection propagates as `Outcome.Failure` so the
 * ViewModel keeps the row it already had. Nothing is cached or queued — a
 * settings change is authoritative and must reach the server or not happen at all.
 */
class StoreSettingsRepositoryImpl(
    private val remote: SettingsRemoteSource,
) : StoreSettingsRepository {

    override suspend fun getSettings(): Outcome<StoreSettings> = outcomeOf {
        remote.readStoreSettings()?.toDomain()
            ?: throw NoSuchElementException("The store settings row is missing.")
    }

    override suspend fun saveSettings(draft: StoreSettingsDraft): Outcome<StoreSettings> = outcomeOf {
        require(draft.id.isNotBlank()) { "The store settings row is missing." }
        require(draft.storeName.isNotBlank()) { "The store name is required." }
        AppLog.i(TAG, "saving store settings")
        remote.updateStoreSettings(draft.id, draft.toWriteFields())?.toDomain()
            ?: throw NoSuchElementException("The store settings row no longer exists.")
    }
}
