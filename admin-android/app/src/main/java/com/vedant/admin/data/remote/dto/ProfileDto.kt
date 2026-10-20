package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `profiles` row.
 *
 * **Column availability: BLOCKED (no admin creds — R1).** `profiles` is
 * RLS-hidden, so its live columns could not be enumerated (see `docs/CONTRACT.md`
 * §10). This DTO therefore declares only the fields the architecture specifies
 * (§5.4) plus the two the older docs mention, and makes every field except `id`
 * nullable. Combined with supabase-kt's `ignoreUnknownKeys`, a live column that
 * is absent or renamed decodes to `null` instead of throwing — the honest
 * behaviour while the schema is unverified.
 *
 * Re-check this DTO after T02 enumerates the real columns.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
)
