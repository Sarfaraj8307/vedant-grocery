package com.vedant.admin.data.remote

import com.vedant.admin.data.remote.dto.ProfileDto
import com.vedant.admin.data.remote.dto.StoreSettingsDto
import com.vedant.admin.data.remote.dto.StoreSettingsUpdateDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * The **PostgREST seam** for store settings and the operator's profile. An
 * interface so the repositories above are unit-testable with a fake — live access
 * to these tables is **[BLOCKED]** (no admin creds), so every read and write is
 * proven against a fake.
 *
 * The settings UPDATE targets the single row by `id` and returns the stored row
 * via `Returning.Representation` (`select()`), so the repository maps only what
 * the server echoed — never the draft it sent.
 */
interface SettingsRemoteSource {

    /** The single `store_settings` row, or `null` when the table is empty. */
    suspend fun readStoreSettings(): StoreSettingsDto?

    /** Updates the row identified by [id] and returns the stored row. */
    suspend fun updateStoreSettings(id: String, update: StoreSettingsUpdateDto): StoreSettingsDto?

    /** The operator's `profiles` row, or `null` when none exists. */
    suspend fun readProfile(userId: String): ProfileDto?
}

/**
 * The real [SettingsRemoteSource], backed by PostgREST.
 *
 * `store_settings` is anon-visible so its columns are known; `profiles` is
 * RLS-hidden and its columns are **BLOCKED** (R1), so its select asks for `*` and
 * the DTO decodes tolerantly.
 */
class SupabaseSettingsRemoteSource(private val client: SupabaseClient) : SettingsRemoteSource {

    override suspend fun readStoreSettings(): StoreSettingsDto? =
        client.postgrest.from(TABLE_STORE_SETTINGS)
            .select(columns = Columns.raw(ALL_COLUMNS)) { limit(1) }
            .decodeList<StoreSettingsDto>()
            .firstOrNull()

    override suspend fun updateStoreSettings(id: String, update: StoreSettingsUpdateDto): StoreSettingsDto? =
        client.postgrest.from(TABLE_STORE_SETTINGS)
            .update(update) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun readProfile(userId: String): ProfileDto? =
        client.postgrest.from(TABLE_PROFILES)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<ProfileDto>()
            .firstOrNull()

    private companion object {
        const val ALL_COLUMNS = "*"
        const val TABLE_STORE_SETTINGS = "store_settings"
        const val TABLE_PROFILES = "profiles"
    }
}
