package com.vedant.admin.testing

import com.vedant.admin.data.local.AppearancePreferences
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fakes for the appearance preference (§8).
 *
 * The fake preferences hold a plain field, so a test can prove the repository
 * really reads its seed from (and really writes through to) storage without a
 * SharedPreferences file existing on a JVM.
 */
class FakeAppearancePreferences(
    initial: AppearanceMode = AppearanceMode.SYSTEM_DEFAULT,
) : AppearancePreferences {

    var stored: AppearanceMode = initial

    var setCalls = 0

    override val mode: AppearanceMode
        get() = stored

    override suspend fun setMode(mode: AppearanceMode) {
        setCalls++
        stored = mode
    }
}

/** A scriptable [AppearanceRepository]. */
class FakeAppearanceRepository(
    initial: AppearanceMode = AppearanceMode.SYSTEM_DEFAULT,
) : AppearanceRepository {

    private val _mode = MutableStateFlow(initial)

    override val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

    var setCalls = 0

    override suspend fun setMode(mode: AppearanceMode) {
        setCalls++
        _mode.update { mode }
    }
}
