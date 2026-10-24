package com.vedant.admin.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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

// ─── Motion duration tokens ──────────────────────────────────────────────
//
// A shared vocabulary so every screen's motion is on the same scale.
// These are intentionally short — this is an operations tool, not a
// marketing site. Motion must never delay an action.

/** 150 ms — quick micro-interactions (press feedback, badge pop). */
const val DURATION_QUICK = 150

/** 300 ms — standard enter/exit transitions (screen content, cards). */
const val DURATION_STANDARD = 300

/** 500 ms — slightly longer reveals (staggered tiles, splash logo). */
const val DURATION_SLOW = 500

// ─── Easing curves ──────────────────────────────────────────────────────
//
// Material's standard easing, expressed as cubic-bezier. Using the same
// family everywhere gives the app a coherent rhythm.

/** Decelerate — incoming elements (enter, rise). */
val EasingDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Accelerate — outgoing elements (exit, fade-out). */
val EasingAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Standard — symmetric, for repetitive pulses and breathes. */
val EasingStandard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

// ─── Stagger helpers ─────────────────────────────────────────────────────

/**
 * The delay before tile [index] starts its enter animation, in ms.
 *
 * Capped at 6 tiles so a long list doesn't make the last items wait
 * seconds before they appear. The stagger is purely decorative — if
 * motion is reduced, every tile is shown immediately.
 */
fun staggerDelay(index: Int, stepMs: Long = 55L): Long = (index * stepMs).coerceAtMost(350L)
