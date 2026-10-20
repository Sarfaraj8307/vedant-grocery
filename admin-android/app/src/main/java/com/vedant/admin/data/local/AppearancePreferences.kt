package com.vedant.admin.data.local

import com.russhwolf.settings.Settings
import com.vedant.admin.domain.model.AppearanceMode

/**
 * Where the persisted appearance choice lives.
 *
 * ## Why multiplatform-settings (reuse, not a new dependency)
 * The session refresh token already persists through `multiplatform-settings`
 * (the SDK's `SettingsSessionManager` uses it under the hood). The appearance
 * choice is the same kind of data — one small, non-relational preference — so
 * it uses the same mechanism rather than adding anything to the build. The
 * `no-arg` artifact supplies the shared-preferences instance with no context
 * plumbing (`Settings()`), exactly as the session manager does.
 *
 * This interface exists so [AppearanceRepositoryImpl] can be unit-tested
 * against a fake without a real SharedPreferences file.
 */
interface AppearancePreferences {

    /** The stored choice, read fresh. Never throws — a bad read is [AppearanceMode.DEFAULT]. */
    val mode: AppearanceMode

    /** Stores [mode]. */
    suspend fun setMode(mode: AppearanceMode)
}

/** The preference key — in one place, like [CacheKeys], so reader/writer cannot drift. */
object AppearanceKeys {
    const val MODE = "appearance_mode"
}

/**
 * The real [AppearancePreferences], backed by multiplatform-settings.
 *
 * A corrupt or hand-edited value cannot crash anything: [AppearanceMode.fromWire]
 * maps every unknown string to [AppearanceMode.DEFAULT].
 */
class SettingsAppearancePreferences(
    private val settings: Settings,
) : AppearancePreferences {

    override val mode: AppearanceMode
        get() = AppearanceMode.fromWire(settings.getStringOrNull(AppearanceKeys.MODE))

    override suspend fun setMode(mode: AppearanceMode) {
        settings.putString(AppearanceKeys.MODE, mode.wire)
    }
}
