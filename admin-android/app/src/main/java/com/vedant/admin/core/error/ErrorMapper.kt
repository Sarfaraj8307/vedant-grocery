package com.vedant.admin.core.error

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Turns any thrown exception into an [AppError].
 *
 * This is the **only** place that knows about Postgres SQLSTATE codes, and the
 * **only** place they are consumed. Everything above the data layer deals in
 * `AppError`, so a raw code can never accidentally reach a screen.
 *
 * supabase-kt surfaces a PostgREST rejection as [RestException], where:
 *  - `error`       — the PostgREST `code` (e.g. `"42501"`, `"PGRST116"`)
 *  - `description` — the PostgREST `message`
 *  - `statusCode`  — the HTTP status
 *
 * The mapper is defensive about which field holds the code: it accepts a known
 * code from either `error` or `description`, so it stays correct even if the SDK
 * changes which field it populates.
 */
object ErrorMapper {

    // Postgres SQLSTATE / PostgREST codes the app understands.
    const val CODE_INSUFFICIENT_PRIVILEGE = "42501"
    const val CODE_INVALID_PARAMETER = "22023"
    const val CODE_CHECK_VIOLATION = "23514"
    const val CODE_UNIQUE_VIOLATION = "23505"
    const val CODE_FOREIGN_KEY_VIOLATION = "23503"
    const val CODE_NO_ROWS_FOR_SINGLE = "PGRST116"

    /**
     * Supabase **Auth** error codes. Auth is a different service from PostgREST
     * and returns a different body shape — `{"code":400,"error_code":"...","msg":"..."}`
     * — so its codes never appear in the SQLSTATE set above. Left unmapped, an
     * auth failure lands in the generic `AppError.Server` branch and the raw code
     * (`invalid_credentials`) is what the operator ends up reading, because
     * [ErrorCopy.sanitize] only strips codes it knows about.
     */
    const val CODE_INVALID_CREDENTIALS = "invalid_credentials"
    const val CODE_EMAIL_NOT_CONFIRMED = "email_not_confirmed"
    const val CODE_USER_ALREADY_EXISTS = "user_already_exists"
    const val CODE_WEAK_PASSWORD = "weak_password"
    const val CODE_OVER_REQUEST_RATE_LIMIT = "over_request_rate_limit"
    const val CODE_EMAIL_NOT_AUTHORIZED = "email_not_authorized"

    /** The generic fallback copy for an unmapped server rejection. */
    const val FALLBACK_SERVER_MESSAGE = "The server rejected the request."

    private val knownCodes = setOf(
        CODE_INSUFFICIENT_PRIVILEGE,
        CODE_INVALID_PARAMETER,
        CODE_CHECK_VIOLATION,
        CODE_UNIQUE_VIOLATION,
        CODE_FOREIGN_KEY_VIOLATION,
        CODE_NO_ROWS_FOR_SINGLE,
    )

    /**
     * Copied from the Supabase Auth service, not invented here. Each maps to copy
     * written for a store owner, never for a developer.
     */
    private val authCodeCopy = mapOf(
        CODE_INVALID_CREDENTIALS to "Incorrect email or password.",
        CODE_EMAIL_NOT_CONFIRMED to
            "This account hasn't been confirmed yet. Confirm it in Supabase, then try again.",
        CODE_USER_ALREADY_EXISTS to "An account with that email already exists.",
        CODE_WEAK_PASSWORD to "That password is too weak — please choose a stronger one.",
        CODE_OVER_REQUEST_RATE_LIMIT to "Too many attempts — please wait a moment and try again.",
        CODE_EMAIL_NOT_AUTHORIZED to "That email address isn't allowed to sign in.",
    )

    fun map(throwable: Throwable): AppError = when (throwable) {
        is RestException -> fromRestException(throwable)
        is HttpRequestTimeoutException -> AppError.Network
        is HttpRequestException -> AppError.Network
        // Local input validation (`require`) is a Validation error, not an
        // unknown one — it is the caller's own precondition failing.
        is IllegalArgumentException ->
            AppError.Validation(throwable.message ?: "That value isn't allowed.")
        // A repository looked up a row the server no longer has (getOrder returned
        // nothing, or a mutation could not be re-fetched). Same meaning as PGRST116.
        is NoSuchElementException ->
            AppError.Server(code = null, message = throwable.message ?: "That record no longer exists.")
        // UnknownHostException, SocketTimeoutException, ConnectException, … all
        // extend IOException and all mean "could not reach the server".
        is IOException -> AppError.Network
        else -> AppError.Unknown(throwable.message ?: throwable::class.simpleName)
    }

    private fun fromRestException(exception: RestException): AppError {
        val code = extractCode(exception.error, exception.description)
        val message = exception.description.orEmpty().ifBlank { exception.message.orEmpty() }
        return fromCode(code, message, exception.statusCode)
    }

    /**
     * Maps a code + message + HTTP status to an [AppError]. Public so the mapping
     * table can be asserted directly by `ErrorMapperTest` without constructing an
     * SDK exception.
     */
    fun fromCode(code: String?, message: String, statusCode: Int?): AppError {
        val normalized = code?.trim().orEmpty()
        // An auth code is matched case-insensitively: the Auth service is a
        // separate product from PostgREST and does not guarantee casing.
        authCodeCopy[normalized.lowercase()]?.let { copy ->
            // 401 during a token refresh means the session is gone, not that the
            // password is wrong — keep that distinction.
            return if (statusCode == 401) AppError.SessionExpired else AppError.Validation(copy)
        }
        return when {
            normalized == CODE_INSUFFICIENT_PRIVILEGE -> AppError.Unauthorized

            normalized == CODE_INVALID_PARAMETER ->
                AppError.Validation(message.ifBlank { "That value isn't allowed." })

            normalized == CODE_CHECK_VIOLATION ->
                AppError.Validation("That value isn't allowed — please check the amount or quantity.")

            normalized == CODE_UNIQUE_VIOLATION ->
                AppError.Validation("That value is already in use.")

            normalized == CODE_FOREIGN_KEY_VIOLATION ->
                AppError.Validation("A related record is missing or already removed.")

            normalized == CODE_NO_ROWS_FOR_SINGLE ->
                AppError.Server(normalized, "That record no longer exists.")

            statusCode == 401 -> AppError.SessionExpired

            else -> AppError.Server(
                code = normalized.ifBlank { null },
                message = message.ifBlank { FALLBACK_SERVER_MESSAGE },
            )
        }
    }

    /** Picks the first field that looks like a code we know; else the raw `error`. */
    private fun extractCode(error: String?, description: String?): String? {
        val candidates = listOf(error, description)
        candidates.firstOrNull { it?.trim() in knownCodes }?.let { return it?.trim() }
        return error?.trim()
    }
}
