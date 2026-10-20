package com.vedant.admin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * A fixed 4 dp base keeps rhythm honest and makes "just a bit more padding"
 * impossible to do by accident. Use [Spacing.md] rather than 16.dp so a global
 * density change is one edit.
 */
@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Standard screen gutter. */
    val screen: Dp = 16.dp,
    /** Vertical gap between list rows in a dense operational list. */
    val rowGap: Dp = 10.dp,
)

/**
 * Corner radii. Deliberately restrained — this is an operations tool, not a
 * consumer app. Nothing here should read as "bubbly".
 */
@Immutable
data class Radius(
    val xs: Dp = 4.dp,
    val sm: Dp = 6.dp,
    val md: Dp = 10.dp,
    val lg: Dp = 14.dp,
    val xl: Dp = 20.dp,
    val pill: Dp = 999.dp,
)

/**
 * Minimum interactive sizes.
 *
 * [touchTarget] is 48 dp, matching the Android accessibility guidance and the
 * brief's "large enough touch targets" requirement. A one-handed operator
 * tapping through orders in a shop should never miss.
 */
@Immutable
data class Sizing(
    val touchTarget: Dp = 48.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 20.dp,
    val iconLg: Dp = 24.dp,
    val avatar: Dp = 36.dp,
    val listThumb: Dp = 52.dp,
    val bottomBar: Dp = 64.dp,
    val topBar: Dp = 56.dp,
    /** Hairline divider. */
    val divider: Dp = 1.dp,
    /** Stroke width for indeterminate progress indicators. */
    val progressStroke: Dp = 2.dp,
    /** Glyph size for empty/error states. */
    val emptyIcon: Dp = 48.dp,
    /** Border width for outlined controls. */
    val border: Dp = 1.dp,

    // --- Placeholder & preview geometry ---------------------------------
    // Skeletons and previews are *shapes*, not content. They live here so they
    // stay on the same scale as the rest of the UI instead of inventing raw
    // values at the call site (the token guard test enforces this).
    /** Catalogue editor preview image. */
    val previewImage: Dp = 96.dp,
    /** A skeleton hero block — an image placeholder. */
    val skeletonHero: Dp = 120.dp,
    /** A skeleton panel block — a form placeholder. */
    val skeletonPanel: Dp = 180.dp,
    /** A wide skeleton line (a name or a value). */
    val skeletonWide: Dp = 160.dp,
    /** A skeleton pill (a status chip). */
    val skeletonChip: Dp = 64.dp,
    /** A skeleton text line. */
    val skeletonLine: Dp = 16.dp,
    /** A short skeleton text line. */
    val skeletonTiny: Dp = 12.dp,
    /** A skeleton value line (a price). */
    val skeletonValue: Dp = 20.dp,
    /** A skeleton row height. */
    val skeletonRow: Dp = 18.dp,
    /** The lift under a pinned action bar. */
    val cardShadow: Dp = 8.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadius = staticCompositionLocalOf { Radius() }
val LocalSizing = staticCompositionLocalOf { Sizing() }