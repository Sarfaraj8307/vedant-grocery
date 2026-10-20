package com.vedant.admin.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast is a **deliverable**, not a formality (T25).
 *
 * Every text/background pair the app actually renders must clear AA in **both**
 * schemes — 4.5:1 for body text, 3:1 for large text. Badge labels are 12 sp, so
 * they are body text and must clear 4.5:1 against their own tint, not 3:1.
 *
 * The test is written so it can fail: [contrastRatio] is pinned against a known
 * reference (black/white is 21:1), and two negative controls assert that the
 * *retired* values really would have failed — which is exactly why the palette
 * was adjusted (see `Color.kt`). A broken formula would make those negatives pass
 * and is caught by the reference check.
 */
class ThemeContrastTest {

    // --- Reference & falsifiability ----------------------------------------

    @Test
    fun `the formula matches the WCAG reference`() {
        // Black on white is the canonical maximum: exactly 21:1.
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.05)
        // And a pair cannot be below 1:1.
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.001)
    }

    @Test
    fun `the retired storefront success green would have failed AA on its tint`() {
        // #1E8E5A on #E6F5EC measured ~3.67:1 — this is the defect that was fixed.
        assertTrue(
            "the negative control no longer fails; the formula is wrong",
            contrastRatio(Color(0xFF1E8E5A), SuccessBg) < BODY,
        )
    }

    @Test
    fun `the retired storefront warning amber would have failed AA on its tint`() {
        // #B26A05 on #FDF3E2 measured ~3.85:1.
        assertTrue(contrastRatio(Color(0xFFB26A05), WarningBg) < BODY)
    }

    @Test
    fun `white text on the dark destructive fill would have failed AA`() {
        // The old dark `onError` was SurfaceWhite on #FFB4AB — only ~1.70:1.
        assertTrue(contrastRatio(Color(0xFFFFFFFF), DangerDark) < BODY)
    }

    // --- Light scheme -------------------------------------------------------

    @Test
    fun `every light-scheme text pair clears AA`() {
        assertPairsClear(LightScheme.textPairs(), "light")
    }

    // --- Dark scheme --------------------------------------------------------

    @Test
    fun `every dark-scheme text pair clears AA`() {
        assertPairsClear(DarkScheme.textPairs(), "dark")
    }

    // --- Brand status roles -------------------------------------------------

    @Test
    fun `each brand status colour clears AA on its own tint`() {
        assertPairsClear(
            listOf(
                Pair("Success on SuccessBg", Success to SuccessBg),
                Pair("Warning on WarningBg", Warning to WarningBg),
                Pair("Danger on DangerBg", Danger to DangerBg),
                Pair("Info on InfoBg", Info to InfoBg),
            ),
            "brand",
        )
    }

    @Test
    fun `the dark destructive content colour clears AA on the destructive fill`() {
        assertTrue(
            "OnDangerDark on DangerDark is ${contrastRatio(OnDangerDark, DangerDark)}",
            contrastRatio(OnDangerDark, DangerDark) >= BODY,
        )
    }

    // --- Helpers ------------------------------------------------------------

    private fun assertPairsClear(pairs: List<Pair<String, Pair<Color, Color>>>, scheme: String) {
        assertTrue("no $scheme pairs to check — the list is empty", pairs.isNotEmpty())
        val failures = pairs.mapNotNull { (name, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            if (ratio < BODY) "%s = %.2f:1 (need %.1f)".format(name, ratio, BODY) else null
        }
        assertTrue("$scheme scheme fails AA: $failures", failures.isEmpty())
    }

    private companion object {

        /** WCAG AA body-text threshold. */
        const val BODY = 4.5

        /**
         * The text/background pairs each scheme actually renders. Kept explicit
         * (rather than derived) so adding a role to the scheme does not silently
         * go unchecked — a reviewer sees the list and asks "is this pair used?".
         */
        fun androidx.compose.material3.ColorScheme.textPairs(): List<Pair<String, Pair<Color, Color>>> =
            listOf(
                "onSurface on surface" to (onSurface to surface),
                "onBackground on background" to (onBackground to background),
                "onSurfaceVariant on surface" to (onSurfaceVariant to surface),
                "onSurfaceVariant on surfaceVariant" to (onSurfaceVariant to surfaceVariant),
                "onPrimary on primary" to (onPrimary to primary),
                "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
                "onSecondaryContainer on secondaryContainer" to
                    (onSecondaryContainer to secondaryContainer),
                "onTertiaryContainer on tertiaryContainer" to
                    (onTertiaryContainer to tertiaryContainer),
                "onError on error" to (onError to error),
                "onErrorContainer on errorContainer" to (onErrorContainer to errorContainer),
            )

        /**
         * WCAG relative luminance of an sRGB colour, from its 0..1 components.
         * See WCAG 2.1 §Relative luminance.
         */
        fun luminance(color: Color): Double {
            fun channel(c: Double): Double =
                if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

            return 0.2126 * channel(color.red.toDouble()) +
                0.7152 * channel(color.green.toDouble()) +
                0.0722 * channel(color.blue.toDouble())
        }

        /** WCAG contrast ratio, always ≥ 1. */
        fun contrastRatio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}
