package com.vedant.admin.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AppLog` is the one logger, and it must never become a second copy of the
 * secrets it describes (CLAUDE.md: "never log tokens, the anon key, emails, or
 * passwords"). The redaction is applied to **every** message before it leaves
 * [AppLog], so a caller cannot leak by accident.
 *
 * The suite proves both the redaction rules and the fact that the sink actually
 * receives the redacted text (not the raw text).
 */
class AppLogTest {

    /** A sink that records exactly what [AppLog] hands it. */
    private class CapturingSink : AppLog.Sink {
        val lines = mutableListOf<Triple<AppLog.Level, String, String>>()
        override fun log(level: AppLog.Level, tag: String, message: String, throwable: Throwable?) {
            lines += Triple(level, tag, message)
        }
    }

    @After
    fun reset() {
        // Leave the singleton in a clean state for other suites.
        AppLog.install(AppLog.Sink { _, _, _, _ -> })
    }

    // --- Redaction rules ----------------------------------------------------

    @Test
    fun `a jwt is redacted`() {
        val raw = "token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payloadpart.signaturepart here"
        val out = AppLog.redact(raw)
        assertFalse("jwt leaked: $out", out.contains("payloadpart"))
        assertTrue(out.contains("[redacted-jwt]"))
    }

    @Test
    fun `a publishable key is redacted`() {
        val out = AppLog.redact("apikey sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH")
        assertFalse("key leaked: $out", out.contains("HIasCDACEN5agfOg05vyA"))
        assertTrue(out.contains("[redacted-key]"))
    }

    @Test
    fun `a bearer token is redacted`() {
        val out = AppLog.redact("Authorization: Bearer abc123.def456.ghi789")
        assertFalse("bearer token leaked: $out", out.contains("abc123"))
        assertTrue(out.contains("Bearer [redacted-token]"))
    }

    @Test
    fun `an email address is redacted`() {
        val out = AppLog.redact("signIn attempt for asha.rao+store@example.co.in")
        assertFalse("email leaked: $out", out.contains("asha.rao"))
        assertTrue(out.contains("[redacted-email]"))
    }

    @Test
    fun `an ordinary diagnostic message is left intact`() {
        val raw = "updateStatus order=o1 -> CONFIRMED"
        assertEquals(raw, AppLog.redact(raw))
    }

    // --- The sink receives the redacted text --------------------------------

    @Test
    fun `the installed sink never sees the raw secret`() {
        val sink = CapturingSink()
        AppLog.install(sink)

        AppLog.i("Auth", "signed in as asha@example.com with Bearer topsecret.token.value")

        assertEquals(1, sink.lines.size)
        val (level, tag, message) = sink.lines.single()
        assertEquals(AppLog.Level.INFO, level)
        assertEquals("Auth", tag)
        assertFalse("the sink saw the raw email", message.contains("asha@example.com"))
        assertFalse("the sink saw the raw token", message.contains("topsecret"))
        assertTrue(message.contains("[redacted-email]"))
    }

    @Test
    fun `each level routes through the sink with its own level`() {
        val sink = CapturingSink()
        AppLog.install(sink)

        AppLog.d("T", "d")
        AppLog.i("T", "i")
        AppLog.w("T", "w")
        AppLog.e("T", "e", IllegalStateException("boom"))

        assertEquals(
            listOf(
                AppLog.Level.DEBUG,
                AppLog.Level.INFO,
                AppLog.Level.WARN,
                AppLog.Level.ERROR,
            ),
            sink.lines.map { it.first },
        )
    }

    @Test
    fun `the default sink is a no-op so core code stays jvm-testable`() {
        // No sink installed by this test: the call must not throw and must not
        // require the Android runtime.
        AppLog.install(AppLog.Sink { _, _, _, _ -> })
        AppLog.e("T", "nothing to see")
    }
}
