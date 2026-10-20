package com.vedant.admin.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ErrorCopy` is the **only** producer of user-facing error strings (architecture
 * §9). Two rules must hold and are asserted directly here:
 *
 *  1. No raw Postgres/PostgREST code ever reaches the operator.
 *  2. Every variant has calm, non-blank wording — a screen never renders an empty
 *     error box.
 *
 * The suite is written so it can fail: [ErrorCopy.containsRawCode] is exercised
 * against a real SQLSTATE, a PostgREST code and clean prose, so a broken pattern
 * is caught rather than silently passing.
 */
class ErrorCopyTest {

    // --- Rule 1: no raw code leaks -----------------------------------------

    @Test
    fun `containsRawCode recognises a SQLSTATE and a PostgREST code`() {
        assertTrue(ErrorCopy.containsRawCode("42501 permission denied"))
        assertTrue(ErrorCopy.containsRawCode("PGRST116 no rows"))
        assertTrue(ErrorCopy.containsRawCode("code 23514"))
    }

    @Test
    fun `containsRawCode leaves ordinary prose alone`() {
        assertFalse(ErrorCopy.containsRawCode("That value is already in use."))
        assertFalse(ErrorCopy.containsRawCode("Quantity must be between 1 and 100."))
    }

    @Test
    fun `sanitize strips a leading code and tidies the whitespace`() {
        assertEquals("permission denied", ErrorCopy.sanitize("42501 permission denied"))
    }

    @Test
    fun `sanitize strips a code embedded mid-sentence`() {
        assertEquals("The value was rejected", ErrorCopy.sanitize("The value 22023 was rejected"))
    }

    @Test
    fun `sanitize removes a postgrest code`() {
        assertEquals("no rows returned", ErrorCopy.sanitize("PGRST116 no rows returned"))
    }

    // --- Rule 1b: legitimate five-digit numbers must survive (OBS-1) ---------
    //
    // The old pattern used `\d{5}`, which stripped *any* five-digit run — a ₹50000
    // amount, a 12345 id — from the server's own wording. The allowlist must strip
    // only the SQLSTATEs the app actually maps. These tests fail against `\d{5}`.

    @Test
    fun `sanitize strips a mapped SQLSTATE that looks like a plain number`() {
        assertEquals("permission denied", ErrorCopy.sanitize("42501 permission denied"))
        assertEquals("invalid input", ErrorCopy.sanitize("22023 invalid input"))
    }

    @Test
    fun `sanitize keeps a five-digit amount that is not a mapped code`() {
        assertEquals("Price must be under 50000", ErrorCopy.sanitize("Price must be under 50000"))
        assertEquals("Order 12345 was rejected", ErrorCopy.sanitize("Order 12345 was rejected"))
    }

    @Test
    fun `sanitize on mixed text strips only the mapped code and keeps the amount`() {
        assertEquals(
            "rejected: max is 50000",
            ErrorCopy.sanitize("42501 rejected: max is 50000"),
        )
    }

    @Test
    fun `a validation message carrying a code is sanitised in the copy`() {
        val copy = ErrorCopy.of(AppError.Validation("42501 permission denied"))
        assertFalse("copy still carries a raw code: $copy", ErrorCopy.containsRawCode(copy))
        assertEquals("permission denied", copy)
    }

    @Test
    fun `a server message carrying a code is sanitised in the copy`() {
        val copy = ErrorCopy.of(AppError.Server(code = "23505", message = "23505 duplicate key"))
        assertFalse("copy still carries a raw code: $copy", ErrorCopy.containsRawCode(copy))
    }

    // --- Rule 2: every variant has non-blank, stable wording ----------------

    @Test
    fun `unauthorized copy is the documented refusal`() {
        assertEquals(
            "You don't have permission to perform this action.",
            ErrorCopy.of(AppError.Unauthorized),
        )
    }

    @Test
    fun `network copy is the documented connection hint`() {
        assertEquals(
            "Couldn't reach the store — check your connection and retry.",
            ErrorCopy.of(AppError.Network),
        )
    }

    @Test
    fun `session-expired copy is the documented sign-in hint`() {
        assertEquals(
            "Your session expired — please sign in again.",
            ErrorCopy.of(AppError.SessionExpired),
        )
    }

    @Test
    fun `unknown copy never echoes the cause`() {
        val copy = ErrorCopy.of(AppError.Unknown("java.lang.IllegalStateException: boom"))
        assertEquals("Something went wrong. Please try again.", copy)
        assertFalse(copy.contains("IllegalStateException"))
    }

    @Test
    fun `a blank validation message falls back to a helpful sentence`() {
        val copy = ErrorCopy.of(AppError.Validation("   "))
        assertTrue("blank validation copy: '$copy'", copy.isNotBlank())
        assertFalse(ErrorCopy.containsRawCode(copy))
    }

    @Test
    fun `a blank server message falls back to the generic rejection`() {
        assertEquals("The server rejected the request.", ErrorCopy.of(AppError.Server(null, "  ")))
    }

    @Test
    fun `every sample message is non-blank and code-free`() {
        val samples = ErrorCopy.sampleMessages()
        assertEquals("one sample per AppError variant", 6, samples.size)
        samples.forEach { message ->
            assertTrue("a sample message was blank", message.isNotBlank())
            assertFalse("a sample leaked a raw code: $message", ErrorCopy.containsRawCode(message))
        }
    }
}
