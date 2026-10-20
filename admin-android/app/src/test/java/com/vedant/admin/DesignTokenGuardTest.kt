package com.vedant.admin

import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.Sizing
import com.vedant.admin.ui.theme.VedantTypography
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two rules the brief calls non-negotiable (T25): **only `ui/theme`
 * tokens — never a hard-coded hex or dp**, and a **12 sp floor** with **48 dp
 * touch targets**.
 *
 * The scan is written so it can fail: [findViolations] is exercised against a
 * deliberately injected literal (which must be detected) and against token-only
 * code (which must be clean), and [scanSourceTree] asserts it really located the
 * tree — so a broken working-directory assumption fails the test instead of
 * silently passing.
 */
class DesignTokenGuardTest {

    // --- Falsifiability -----------------------------------------------------

    @Test
    fun `the scanner flags injected hard-coded literals`() {
        val injected = """
            val a = Color(0xFF123456)
            val b = 12.dp
            val c = 10.sp
            val d = Color.Red
        """.trimIndent()

        val found = findViolations("feature/example/Example.kt", injected)

        assertTrue("missed a hex literal", found.any { it.contains("colour") })
        assertTrue("missed a dp literal", found.any { it.contains("dp") })
        assertTrue("missed an sp literal", found.any { it.contains("sp") })
    }

    @Test
    fun `the scanner leaves token-only code clean`() {
        val clean = """
            val pad = VedantTheme.spacing.md
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            val size = VedantTheme.sizing.touchTarget
        """.trimIndent()

        assertTrue(findViolations("feature/example/Example.kt", clean).isEmpty())
    }

    @Test
    fun `the theme package is exempt by design`() {
        assertTrue(isThemeFile("app/src/main/java/com/vedant/admin/ui/theme/Color.kt"))
        assertFalse(isThemeFile("app/src/main/java/com/vedant/admin/feature/orders/list/OrdersListScreen.kt"))
    }

    // --- The real tree ------------------------------------------------------

    @Test
    fun `the scan actually inspects the source tree`() {
        val root = sourceRoot()
        assertNotNull("could not locate the main source root from '${File(".").absolutePath}'", root)
        assertTrue(
            "no Kotlin files found under ${root?.path}",
            root!!.walkTopDown().count { it.isFile && it.name.endsWith(".kt") } > 20,
        )
    }

    @Test
    fun `no colour literal is hard-coded outside ui-theme`() {
        assertTrue("hard-coded colours: ${scanSourceTree().filter { it.contains("colour") }}",
            scanSourceTree().none { it.contains("colour") })
    }

    @Test
    fun `no dp literal is hard-coded outside ui-theme`() {
        assertTrue("hard-coded dp: ${scanSourceTree().filter { it.contains("dp literal") }}",
            scanSourceTree().none { it.contains("dp literal") })
    }

    @Test
    fun `no sp literal is hard-coded outside ui-theme`() {
        assertTrue("hard-coded sp: ${scanSourceTree().filter { it.contains("sp literal") }}",
            scanSourceTree().none { it.contains("sp literal") })
    }

    // --- Token values -------------------------------------------------------

    @Test
    fun `the touch target is 48 dp`() {
        assertEquals(48f, Sizing().touchTarget.value, 0f)
    }

    @Test
    fun `every type style meets the 12 sp floor`() {
        val styles = listOf(
            "headlineLarge" to VedantTypography.headlineLarge,
            "headlineMedium" to VedantTypography.headlineMedium,
            "headlineSmall" to VedantTypography.headlineSmall,
            "titleLarge" to VedantTypography.titleLarge,
            "titleMedium" to VedantTypography.titleMedium,
            "titleSmall" to VedantTypography.titleSmall,
            "bodyLarge" to VedantTypography.bodyLarge,
            "bodyMedium" to VedantTypography.bodyMedium,
            "bodySmall" to VedantTypography.bodySmall,
            "labelLarge" to VedantTypography.labelLarge,
            "labelMedium" to VedantTypography.labelMedium,
            "labelSmall" to VedantTypography.labelSmall,
            "displayLarge" to VedantTypography.displayLarge,
            "displayMedium" to VedantTypography.displayMedium,
            "displaySmall" to VedantTypography.displaySmall,
            "NumericEmphasis" to NumericEmphasis,
        )

        val belowFloor = styles.filter { (_, style) -> style.fontSize.value < 12f }
        assertTrue("styles below the 12 sp floor: ${belowFloor.map { it.first }}", belowFloor.isEmpty())
    }

    // --- Helpers ------------------------------------------------------------

    private fun sourceRoot(): File? =
        listOf(
            File("app/src/main/java/com/vedant/admin"),
            File("src/main/java/com/vedant/admin"),
        ).firstOrNull { it.isDirectory }

    private fun scanSourceTree(): List<String> {
        val root = sourceRoot() ?: return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filterNot { isThemeFile(it.path) }
            .flatMap { file ->
                val text = runCatching { file.readText() }.getOrDefault("")
                findViolations(file.path, text).asSequence()
            }
            .toList()
    }

    private fun isThemeFile(path: String): Boolean =
        path.replace('\\', '/').contains("/ui/theme/")

    private fun findViolations(path: String, source: String): List<String> {
        val code = stripComments(source)
        val violations = mutableListOf<String>()
        if (COLOR_LITERAL.containsMatchIn(code)) violations += "$path: hard-coded colour"
        if (DP_LITERAL.containsMatchIn(code)) violations += "$path: hard-coded dp literal"
        if (SP_LITERAL.containsMatchIn(code)) violations += "$path: hard-coded sp literal"
        return violations
    }

    /**
     * Drops line and block comments so a doc comment that *mentions* a value
     * (e.g. "rather than 16.dp") is not a false positive. Real code on a line is
     * kept, so a literal in code can never be hidden behind a trailing comment.
     */
    private fun stripComments(source: String): String =
        source.lineSequence().joinToString("\n") { line ->
            val withoutLineComment = line.substringBefore("//")
            val head = withoutLineComment.trimStart()
            if (head.startsWith("*") || head.startsWith("/*")) "" else withoutLineComment
        }

    private companion object {
        val COLOR_LITERAL = Regex(
            """Color\(0x[0-9A-Fa-f]+\)|Color\.(Red|White|Black|Blue|Green|Yellow|Gray|Magenta|Cyan)\b""",
        )
        val DP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
        val SP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.sp\b""")
    }
}
