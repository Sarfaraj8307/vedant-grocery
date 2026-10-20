package com.vedant.admin.domain.model

/**
 * The operator's profile row.
 *
 * The `profiles` table is RLS-hidden, so its exact live column list is
 * **BLOCKED (no admin creds — R1)** — see `docs/CONTRACT.md` §10. This model
 * therefore carries only the fields the architecture specifies (§5.4) plus the
 * two the stale docs also mention (`username`, `role`), each nullable so a
 * missing or renamed column decodes as `null` rather than throwing. The DTO that
 * produces this is tolerant (`ignoreUnknownKeys`), so extra live columns are
 * harmless.
 */
data class Profile(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val role: String? = null,
) {
    /** A best-effort human name for the top bar; falls back to the username, then the id. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { username.orEmpty() }
            .ifBlank { email.orEmpty() }
            .ifBlank { id }
}
