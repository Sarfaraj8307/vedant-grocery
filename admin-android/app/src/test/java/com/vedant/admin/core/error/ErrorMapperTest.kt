package com.vedant.admin.core.error

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The error taxonomy is the boundary between "the database said no" and "the
 * owner sees a sentence". A wrong mapping shows a raw code to a person or hides
 * a real permission failure behind "something went wrong", so the whole table is
 * asserted here.
 */
class ErrorMapperTest {

    private fun rest(code: String, message: String, status: Int = 400) =
        RestException(code, message, status, message)

    // --- Postgres / PostgREST codes ----------------------------------------

    @Test
    fun `insufficient privilege maps to Unauthorized`() {
        assertEquals(AppError.Unauthorized, ErrorMapper.map(rest("42501", "permission denied")))
    }

    @Test
    fun `invalid parameter maps to Validation carrying the server message`() {
        val mapped = ErrorMapper.map(rest("22023", "delivery slot full"))
        assertTrue("expected Validation, was $mapped", mapped is AppError.Validation)
        assertEquals("delivery slot full", (mapped as AppError.Validation).message)
    }

    @Test
    fun `check violation maps to Validation`() {
        assertTrue(ErrorMapper.map(rest("23514", "check failed")) is AppError.Validation)
    }

    @Test
    fun `unique violation maps to Validation`() {
        assertTrue(ErrorMapper.map(rest("23505", "duplicate key")) is AppError.Validation)
    }

    @Test
    fun `foreign key violation maps to Validation`() {
        assertTrue(ErrorMapper.map(rest("23503", "fk missing")) is AppError.Validation)
    }

    @Test
    fun `no rows for single maps to Server`() {
        val mapped = ErrorMapper.map(rest("PGRST116", "no rows", 406))
        assertTrue("expected Server, was $mapped", mapped is AppError.Server)
        assertEquals("PGRST116", (mapped as AppError.Server).code)
    }

    @Test
    fun `http 401 maps to SessionExpired`() {
        assertEquals(AppError.SessionExpired, ErrorMapper.map(rest("", "jwt expired", 401)))
    }

    // --- Transport failures ------------------------------------------------

    @Test
    fun `request timeout maps to Network`() {
        assertEquals(
            AppError.Network,
            ErrorMapper.map(HttpRequestTimeoutException("https://example.test", 1_000L)),
        )
    }

    @Test
    fun `socket timeout maps to Network`() {
        assertEquals(AppError.Network, ErrorMapper.map(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `unknown host maps to Network`() {
        assertEquals(AppError.Network, ErrorMapper.map(UnknownHostException("no dns")))
    }

    @Test
    fun `generic io exception maps to Network`() {
        assertEquals(AppError.Network, ErrorMapper.map(IOException("broken pipe")))
    }

    // --- Local validation & the catch-all ----------------------------------

    @Test
    fun `local require failure maps to Validation`() {
        val mapped = ErrorMapper.map(IllegalArgumentException("Enter your email address."))
        assertTrue(mapped is AppError.Validation)
        assertEquals("Enter your email address.", (mapped as AppError.Validation).message)
    }

    @Test
    fun `unexpected throwable maps to Unknown and keeps the cause`() {
        val mapped = ErrorMapper.map(IllegalStateException("boom"))
        assertTrue("expected Unknown, was $mapped", mapped is AppError.Unknown)
        assertEquals("boom", (mapped as AppError.Unknown).cause)
    }

    // --- The table is complete --------------------------------------------

    @Test
    fun `fromCode covers every documented code`() {
        assertEquals(
            AppError.Unauthorized,
            ErrorMapper.fromCode(ErrorMapper.CODE_INSUFFICIENT_PRIVILEGE, "x", 403),
        )
        assertTrue(
            ErrorMapper.fromCode(ErrorMapper.CODE_INVALID_PARAMETER, "x", 400) is AppError.Validation,
        )
        assertTrue(
            ErrorMapper.fromCode(ErrorMapper.CODE_CHECK_VIOLATION, "x", 400) is AppError.Validation,
        )
        assertTrue(
            ErrorMapper.fromCode(ErrorMapper.CODE_UNIQUE_VIOLATION, "x", 409) is AppError.Validation,
        )
        assertTrue(
            ErrorMapper.fromCode(ErrorMapper.CODE_FOREIGN_KEY_VIOLATION, "x", 409) is AppError.Validation,
        )
        assertTrue(
            ErrorMapper.fromCode(ErrorMapper.CODE_NO_ROWS_FOR_SINGLE, "x", 406) is AppError.Server,
        )
        assertEquals(AppError.SessionExpired, ErrorMapper.fromCode(null, "x", 401))
        assertTrue(ErrorMapper.fromCode(null, "x", 500) is AppError.Server)
    }

    @Test
    fun `an unknown code still becomes a Server error with the code preserved`() {
        val mapped = ErrorMapper.fromCode("99999", "weird", 418)
        assertTrue(mapped is AppError.Server)
        assertEquals("99999", (mapped as AppError.Server).code)
    }

    // --- Copy never leaks a raw code ---------------------------------------

    @Test
    fun `no user-facing copy contains a raw code`() {
        ErrorCopy.sampleMessages().forEach { copy ->
            assertFalse("copy leaks a raw code: '$copy'", ErrorCopy.containsRawCode(copy))
        }
    }

    @Test
    fun `server copy is sanitised when the message is only a code`() {
        val copy = ErrorCopy.of(AppError.Server("PGRST116", "PGRST116"))
        assertFalse("copy leaks a raw code: '$copy'", ErrorCopy.containsRawCode(copy))
        assertTrue(copy.isNotBlank())
    }

    @Test
    fun `server copy strips an embedded code but keeps the useful text`() {
        val copy = ErrorCopy.of(AppError.Server("42501", "42501 permission denied for table orders"))
        assertFalse(copy, ErrorCopy.containsRawCode(copy))
        assertTrue(copy, copy.contains("permission denied"))
    }

    // --- Supabase Auth codes -------------------------------------------------
    //
    // Auth is a separate service with its own codes. These are the exact bodies
    // the live project returned on 2026-10-14, and the omission of this case is
    // what let `Auth API error: invalid_credentials` reach a real sign-in banner.

    @Test
    fun `invalid credentials maps to the friendly sign-in copy, not the raw code`() {
        // Shape of the live response: {"code":400,"error_code":"invalid_credentials", ...}
        val mapped = ErrorMapper.fromCode("invalid_credentials", "Invalid login credentials", 400)
        assertTrue("expected Validation, was $mapped", mapped is AppError.Validation)
        assertEquals("Incorrect email or password.", (mapped as AppError.Validation).message)
        assertFalse(
            "the raw code reached user copy",
            ErrorCopy.containsRawCode(ErrorCopy.of(mapped)),
        )
    }

    @Test
    fun `email not confirmed maps to actionable copy`() {
        val mapped = ErrorMapper.fromCode("email_not_confirmed", "Email not confirmed", 400)
        assertTrue(mapped is AppError.Validation)
        assertTrue(
            (mapped as AppError.Validation).message.contains("confirmed"),
        )
    }

    @Test
    fun `auth codes are matched case-insensitively`() {
        assertTrue(
            ErrorMapper.fromCode("INVALID_CREDENTIALS", "x", 400) is AppError.Validation,
        )
    }

    @Test
    fun `an auth code on a 401 is a session expiry, not a bad password`() {
        // A token refresh failing must route to sign-in, not say "wrong password".
        assertEquals(
            AppError.SessionExpired,
            ErrorMapper.fromCode("invalid_credentials", "jwt expired", 401),
        )
    }

    @Test
    fun `an unknown auth code is stripped from copy rather than shown`() {
        // The backstop: a code the mapper has never seen must still not leak.
        val copy = ErrorCopy.of(AppError.Server("some_future_code", "Auth API error: some_future_code"))
        assertFalse("copy leaks a raw provider code: '$copy'", ErrorCopy.containsRawCode(copy))
        assertTrue(copy.isNotBlank())
    }

    @Test
    fun `sanitising does not eat ordinary prose or numbers`() {
        // Regression guard on the snake_case backstop: it must not mangle a
        // validation message that carries a price or a plain sentence.
        val copy = ErrorCopy.of(AppError.Validation("Price must be under 12000 rupees."))
        assertTrue(copy, copy.contains("12000"))
        assertTrue(copy, copy.contains("Price must be under"))
    }
}
