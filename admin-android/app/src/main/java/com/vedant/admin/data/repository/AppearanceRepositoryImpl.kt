package com.vedant.admin.data.repository

import com.vedant.admin.data.local.AppearancePreferences
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * The [AppearanceRepository] over an [AppearancePreferences] store.
 *
 * The [mode] flow is seeded **synchronously** in the constructor (preferences
 * are a local read, so this is cheap and never blocks a thread that matters),
 * which is what lets `MainActivity` apply the saved theme on the first frame
 * of a cold start. Writes are serialised by a mutex so a fast double-tap on
 * two options cannot interleave write/emit and leave the flow out of step with
 * storage.
 */
class AppearanceRepositoryImpl(
    private val preferences: AppearancePreferences,
) : AppearanceRepository {

    private val writeLock = Mutex()

    private val _mode = MutableStateFlow(preferences.mode)

    override val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

    override suspend fun setMode(mode: AppearanceMode) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            preferences.setMode(mode)
            _mode.update { mode }
        }
    }
}
