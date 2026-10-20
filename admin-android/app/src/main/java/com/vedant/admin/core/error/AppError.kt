package com.vedant.admin.core.error

/**
 * The closed error taxonomy. Every failure the app can show a user is one of
 * these six — nothing else reaches the UI.
 *
 * Raw Postgres/PostgREST codes (`42501`, `22023`, `PGRST116`, …) are mapped into
 * these by [ErrorMapper] and are **never** shown to a person (PRD P0-9).
 */
sealed interface AppError {

    /** `42501` — the server refused because the caller is not allowed. */
    data object Unauthorized : AppError

    /** `22023` / `23514` / `23505` / `23503` — the server rejected the values. */
    data class Validation(val message: String, val field: String? = null) : AppError

    /** Timeout or no connection. */
    data object Network : AppError

    /** `401` / a refresh that failed — the session is gone; route to sign-in. */
    data object SessionExpired : AppError

    /** Any other PostgREST/RPC rejection that is not a validation or auth error. */
    data class Server(val code: String?, val message: String) : AppError

    /** Anything unexpected. Always logged; shown as a generic message. */
    data class Unknown(val cause: String? = null) : AppError
}
