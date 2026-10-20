package com.vedant.admin.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vedant.admin.domain.model.AppearanceMode

/**
 * Colour scheme.
 *
 * Brand hues are identical in light and dark — Leaf and Marigold were chosen to
 * clear contrast on both the warm paper and the dark neutral, so a status never
 * changes meaning with the system theme. Only neutrals invert.
 *
 * `surfaceVariant` carries the muted background used for section headers and
 * inset panels; `outlineVariant` is the hairline divider.
 *
 * `internal` (not private) so `ThemeContrastTest` can assert every text pair
 * clears WCAG AA in **both** schemes — contrast is a deliverable, not a hope.
 */
internal val LightScheme = lightColorScheme(
    primary = Leaf600,
    onPrimary = SurfaceWhite,
    primaryContainer = Leaf100,
    onPrimaryContainer = Leaf900,

    secondary = Marigold600,
    onSecondary = SurfaceWhite,
    secondaryContainer = Marigold100,
    onSecondaryContainer = Marigold700,

    tertiary = Berry600,
    onTertiary = SurfaceWhite,
    tertiaryContainer = Berry100,
    onTertiaryContainer = Berry700,

    background = Paper,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkMuted,
    surfaceContainerHighest = SurfaceSunken,

    outline = LineStrong,
    outlineVariant = Line,

    error = Danger,
    onError = SurfaceWhite,
    errorContainer = DangerBg,
    onErrorContainer = Danger,
)

internal val DarkScheme = darkColorScheme(
    primary = Leaf400,
    onPrimary = Leaf900,
    primaryContainer = Leaf800,
    onPrimaryContainer = Leaf100,

    secondary = Marigold400,
    onSecondary = Marigold700,
    secondaryContainer = Marigold700,
    onSecondaryContainer = Marigold100,

    tertiary = Berry500,
    onTertiary = SurfaceWhite,
    tertiaryContainer = Berry700,
    onTertiaryContainer = Berry100,

    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerHighest = SurfaceSunkenDark,

    outline = LineStrongDark,
    outlineVariant = LineDark,

    error = DangerDark,
    onError = OnDangerDark,
    errorContainer = DangerDarkBg,
    onErrorContainer = DangerDark,
)

/**
 * App theme.
 *
 * Deliberately **no** dynamic colour: this is an operational tool where a status
 * colour must mean exactly one thing everywhere. Letting the wallpaper repaint
 * "ready" or "cancelled" would be a usability regression, not a delight.
 *
 * The system status/navigation bars are tinted to match the surface so the app
 * reads edge-to-edge without a seam.
 */
@Composable
fun VedantAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalRadius provides Radius(),
        LocalSizing provides Sizing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VedantTypography,
            content = content,
        )
    }
}

/**
 * The operator's persisted appearance choice (§8).
 *
 * Provided once at the `MainActivity` boundary (where `LocalReduceMotion` is
 * provided) and read anywhere below it — e.g. by the settings screen that
 * shows which option is currently active. It is the composition-local mirror
 * of `AppearanceRepository.mode`; the repository stays the source of truth
 * and this local only avoids threading the value through every screen.
 *
 * Defaults to [AppearanceMode.SYSTEM_DEFAULT] so a screen rendered outside the
 * provider (a preview) simply follows the system.
 */
val LocalAppearanceMode = staticCompositionLocalOf { AppearanceMode.SYSTEM_DEFAULT }

/** Shorthand accessors so screens read `Spacing.md`, not `LocalSpacing.current.md`. */
object VedantTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
    val radius: Radius
        @Composable get() = LocalRadius.current
    val sizing: Sizing
        @Composable get() = LocalSizing.current
}