package com.vedant.admin.core.error

/**
 * The **only** producer of user-facing error strings.
 *
 * Two rules hold here:
 *  1. Raw Postgres/PostgREST codes never appear in the output. Server-supplied
 *     text is passed through [sanitize], which strips the SQLSTATEs [ErrorMapper]
 *     actually maps (`42501`, `22023`, `23514`, `23505`, `23503`), any PostgREST
 *     code (`PGRST116`), and any `snake_case` Auth provider code
 *     (`invalid_credentials`, `email_not_confirmed`, …). It does **not** strip
 *     arbitrary numbers — a price or a quantity in the server's own wording must
 *     survive.
 *  2. The wording is calm and actionable — it tells the owner what to do next,
 *     never what the database thought.
 */
object ErrorCopy {

    /**
     * The SQLSTATEs this app actually surfaces, taken from [ErrorMapper] so the
     * two stay in step. Deliberately an **allowlist, not `\d{5}`**: a generic
     * five-digit matcher would mangle legitimate server text — e.g.
     * `"Price must be under 12000"` became `"Price must be under"`. Only a code
     * we know we map is stripped.
     */
    private val SURFACED_SQLSTATES = listOf(
        ErrorMapper.CODE_INSUFFICIENT_PRIVILEGE,
        ErrorMapper.CODE_INVALID_PARAMETER,
        ErrorMapper.CODE_CHECK_VIOLATION,
        ErrorMapper.CODE_UNIQUE_VIOLATION,
        ErrorMapper.CODE_FOREIGN_KEY_VIOLATION,
    )

    private val RAW_CODE_PATTERN = Regex(
        """\b(?:PGRST\d+|${SURFACED_SQLSTATES.joinToString("|")})\b""",
    )

    /**
     * Supabase Auth speaks in `snake_case` codes (`invalid_credentials`,
     * `email_not_confirmed`, …). [ErrorMapper] maps the ones this app knows, but
     * this pattern is the backstop: if Auth ever adds a code the mapper has not
     * learned, the operator still never reads the raw token. It is deliberately
     * narrow — a whole word that is lowercase letters/underscores only — so it
     * cannot eat normal prose like `"Price must be under 12000"`.
     */
    private val PROVIDER_CODE_PATTERN = Regex("""\b[a-z]+(?:_[a-z]+)+\b""")

    private val WHITESPACE_PATTERN = Regex("""\s{2,}""")

    fun of(error: AppError): String = when (error) {
        AppError.Unauthorized -> "You don't have permission to perform this action."
        is AppError.Validation -> sanitize(error.message)
            .ifBlank { "That value isn't allowed — please check and try again." }
        AppError.Network -> "Couldn't reach the store — check your connection and retry."
        AppError.SessionExpired -> "Your session expired — please sign in again."
        is AppError.Server -> sanitize(error.message).ifBlank { "The server rejected the request." }
        is AppError.Unknown -> "Something went wrong. Please try again."
    }

    /** True when [text] contains a raw database/PostgREST or Auth provider code. */
    fun containsRawCode(text: String): Boolean =
        RAW_CODE_PATTERN.containsMatchIn(text) || PROVIDER_CODE_PATTERN.containsMatchIn(text)

    /**
     * Removes raw codes from server-supplied text and tidies the leftover
     * whitespace, so `"42501 permission denied"` becomes `"permission denied"`
     * and `"Auth API error: invalid_credentials"` becomes `"Auth API error:"`.
     *
     * When stripping leaves nothing useful, the caller's `ifBlank` fallback takes
     * over — which is why `of()` always pairs this with a non-blank default.
     */
    fun sanitize(message: String): String =
        message.replace(RAW_CODE_PATTERN, " ")
            .replace(PROVIDER_CODE_PATTERN, " ")
            .replace(WHITESPACE_PATTERN, " ")
            .trim()

    /**
     * One representative message per [AppError] variant, in the same order.
     * `ErrorMapperTest` scans these to prove no copy leaks a raw code.
     */
    fun sampleMessages(): List<String> = listOf(
        of(AppError.Unauthorized),
        of(AppError.Validation("Quantity must be between 1 and 100.")),
        of(AppError.Network),
        of(AppError.SessionExpired),
        of(AppError.Server(ErrorMapper.CODE_NO_ROWS_FOR_SINGLE, "That record no longer exists.")),
        of(AppError.Unknown("IllegalStateException")),
    )
}
