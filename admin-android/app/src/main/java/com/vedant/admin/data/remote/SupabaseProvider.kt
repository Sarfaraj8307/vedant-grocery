package com.vedant.admin.data.remote

import com.vedant.admin.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/**
 * Builds the single Supabase client. Called **once**, from `AppContainer`.
 *
 * No composable and no ViewModel ever calls this — the client lives inside the
 * DI container and is handed only to the repository implementations in the
 * data layer.
 *
 * ## Key hygiene
 * Only the **publishable** key is read (`BuildConfig.SUPABASE_ANON_KEY`), the
 * same one the customer PWA ships. It is designed to be public; every privileged
 * operation is gated server-side by RLS and `private.is_admin()`. No service-role
 * key is referenced anywhere in this project — `SecurityKeyHygieneTest` fails the
 * build if one appears in `BuildConfig` or resources.
 *
 * ## Verified against supabase-kt 3.0.3
 * The Auth-config property names below (`sessionManager`, `autoLoadFromStorage`,
 * `autoSaveToStorage`, `alwaysAutoRefresh`) were confirmed by inspecting the
 * resolved `auth-kt-android-debug:3.0.3` artifact, not assumed. `SettingsSessionManager`
 * has a no-argument constructor that uses the no-arg `Settings` from
 * `multiplatform-settings-no-arg`, so no `Settings()` argument is required.
 */
object SupabaseProvider {

    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            // Persist the refresh token across process death via SharedPreferences.
            sessionManager = SettingsSessionManager()
            autoLoadFromStorage = true
            autoSaveToStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest) {
            // Tolerant decode is mandatory: several live columns are still
            // UNKNOWN (BLOCKED — no admin creds), so a row may carry columns our
            // DTOs do not declare. `ignoreUnknownKeys` turns "extra column" into
            // "ignored" instead of a decode crash; `explicitNulls = false` keeps a
            // missing column equal to a null one.
            serializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                },
            )
        }
        install(Storage)
        // Realtime is deliberately NOT installed: not required by the PRD, and it
        // would add a socket dependency for no operational benefit.
    }
}
