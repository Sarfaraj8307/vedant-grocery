package com.vedant.admin.domain.model

/**
 * The operator's appearance choice (§8).
 *
 * The theme already ships both colour schemes; this enum is the missing piece
 * — a **user-selectable** preference that survives a restart. It follows the
 * platform convention of three options: defer to the system, or pin light/dark
 * regardless of what the system is doing.
 *
 * The `wire` values are what is persisted. They are deliberately short, stable
 * and never shown to the user — [fromWire] decodes them back, and an unknown
 * value (an older build, a corrupted write) degrades to [SYSTEM_DEFAULT]
 * rather than to a guessed theme.
 */
enum class AppearanceMode(
    /** The persisted representation. Never renumbered or reworded. */
    val wire: String,
    /** The operator-facing label for the settings control. */
    val label: String,
) {
    /** Follow the system dark-mode setting. */
    SYSTEM_DEFAULT("system", "System default"),

    /** Always the light scheme. */
    LIGHT("light", "Light"),

    /** Always the dark scheme. */
    DARK("dark", "Dark"),
    ;

    /**
     * Resolves this choice to the `darkTheme` flag the theme composable takes.
     *
     * [SYSTEM_DEFAULT] defers to [systemInDark] (read once at the activity
     * boundary); the other two pin the answer so the operator's choice wins
     * over the platform's.
     */
    fun prefersDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM_DEFAULT -> systemInDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** The choice used before the operator has picked one. */
        val DEFAULT: AppearanceMode = SYSTEM_DEFAULT

        /**
         * Decodes a persisted value. Case-insensitive and trim-tolerant; an
         * absent or unknown value yields [DEFAULT] — the safest answer,
         * because it defers to the system the operator already set up.
         */
        fun fromWire(raw: String?): AppearanceMode {
            val normalized = raw?.trim()?.lowercase() ?: return DEFAULT
            return entries.firstOrNull { it.wire == normalized } ?: DEFAULT
        }
    }
}
