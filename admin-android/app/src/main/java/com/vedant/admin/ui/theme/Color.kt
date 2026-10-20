package com.vedant.admin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Vedant brand palette.
 *
 * These are the *same* values as the customer PWA's `@theme` tokens in
 * `src/app/globals.css`, so the admin app and the storefront read as one
 * business. Do not invent new hues here; add to the PWA token set first if a
 * genuinely new role is needed.
 *
 * Roles are expressed semantically in `VedantColors` rather than by raw scale
 * step, so a screen never hard-codes `Leaf600` where it actually means "success".
 */

// --- Leaf: primary brand, confirmations, "good" states ---------------------
val Leaf50 = Color(0xFFF2F8F3)
val Leaf100 = Color(0xFFDFEEE2)
val Leaf200 = Color(0xFFBFDCC6)
val Leaf300 = Color(0xFF93C2A1)
val Leaf400 = Color(0xFF5FA277)
val Leaf500 = Color(0xFF3D855A)
val Leaf600 = Color(0xFF2A6B46)
val Leaf700 = Color(0xFF1F5537)
val Leaf800 = Color(0xFF1A432D)
val Leaf900 = Color(0xFF142F21)

// --- Marigold: attention, in-progress, warnings ---------------------------
val Marigold50 = Color(0xFFFFF8EC)
val Marigold100 = Color(0xFFFDEBDB)
val Marigold200 = Color(0xFFFAD79A)
val Marigold300 = Color(0xFFF5BE5F)
val Marigold400 = Color(0xFFEFA632)
val Marigold500 = Color(0xFFDE8E18)
val Marigold600 = Color(0xFFB96F10)
val Marigold700 = Color(0xFF8F520D)

// --- Berry: destructive / cancelled --------------------------------------
val Berry100 = Color(0xFFFBE8F0)
val Berry500 = Color(0xFFA93A63)
val Berry600 = Color(0xFF8B2E5C)
val Berry700 = Color(0xFF6E2449)

// --- Neutrals: the warm paper scale --------------------------------------
val Paper = Color(0xFFFDFCF7)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFFAF6ED)
val SurfaceSunken = Color(0xFFEFE9D9)
val Line = Color(0xFFE8DDD0)
val LineStrong = Color(0xFFD4C4A8)
val Ink = Color(0xFF1A2E1A)
val InkMuted = Color(0xFF5C6B52)
val InkSubtle = Color(0xFF8A9A7B)

// --- Status roles --------------------------------------------------------
//
// Hue family comes from the PWA token set so the two apps read as one business.
// The *lightness* of [Success] and [Warning] was pulled down from the storefront
// values, however, because the admin app renders them as **12 sp badge text on
// their own tinted `*Bg`**, and the original values only cleared ~3.7:1 there —
// below WCAG AA's 4.5:1 for body text (see `ThemeContrastTest`). The storefront
// paints them on white, where the originals were fine. Darkening the foreground
// is the only fix: the tints are already near-white, so lightening them cannot
// reach 4.5:1. Every consumer of these tokens (badges, warning banners) reads
// them as text on the tint, so this is the correct value for this app's use.
val Success = Color(0xFF157A47)
val SuccessBg = Color(0xFFE6F5EC)
val Warning = Color(0xFF9A5C04)
val WarningBg = Color(0xFFFDF3E2)
val Danger = Color(0xFFB3261E)
val DangerBg = Color(0xFFFDECEA)
val Info = Color(0xFF1F6FB2)
val InfoBg = Color(0xFFEAF2FA)

/**
 * Dark-mode neutrals. The brand hues stay put (Leaf/Marigold/Berry read
 * correctly on both surfaces); only the neutrals invert.
 */
val PaperDark = Color(0xFF12180F)
val SurfaceDark = Color(0xFF1A2117)
val SurfaceMutedDark = Color(0xFF212A1D)
val SurfaceSunkenDark = Color(0xFF2A3425)
val LineDark = Color(0xFF35402E)
val LineStrongDark = Color(0xFF4A5741)
val InkDark = Color(0xFFE8EDE4)
val InkMutedDark = Color(0xFFA9B5A0)
val InkSubtleDark = Color(0xFF7C8A74)

/**
 * Destructive role on a dark surface. The light `Danger` (#B3261E) is far too
 * dark to read on a near-black background, so dark mode uses a light red.
 *
 * Because the *fill* is now light, its content colour must be dark — white text
 * on #FFB4AB is only ~1.7:1. [OnDangerDark] is the Material-canonical dark-red
 * content colour and clears 7.7:1 on the fill.
 */
val DangerDark = Color(0xFFFFB4AB)
val DangerDarkBg = Color(0xFF3B1512)
val OnDangerDark = Color(0xFF690005)