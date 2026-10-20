package com.vedant.admin.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the platform asks for **reduced motion**.
 *
 * Driven by the system's animator duration scale (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`,
 * which is what "Remove animations" sets in Accessibility settings). When true,
 * the app's decorative motion is replaced with a static equivalent — a transition
 * becomes an instant swap, the skeleton stops pulsing.
 *
 * Motion in this app is **always cosmetic**: it never gates an operational
 * action, so honouring this preference cannot hide or delay anything. Defaults to
 * `false` so a screen shown outside the provider (e.g. a preview) animates
 * normally.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Reads the system's reduced-motion preference.
 *
 * A missing or unreadable setting is treated as "no preference" — the app should
 * animate rather than silently disable motion for everyone because a settings
 * read failed.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}
