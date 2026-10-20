package com.vedant.admin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vedant.admin.core.logging.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private const val TAG = "CacheStore"

/**
 * A tiny key/value snapshot store for **last-good reads**.
 *
 * ### DataStore, not Room (explicit brief)
 * The cache holds JSON documents produced by kotlinx-serialization, keyed by a
 * stable string. There is no schema, no query, and no migration — so a
 * DataStore file of string preferences is the right weight, and it avoids an
 * annotation processor (no KSP) entirely.
 *
 * ### What is cached, and what is not
 * Only **reads** are ever written here. A mutation is never recorded as a
 * success — the whole point of "never fake success" is that a write's outcome is
 * the server's, and a stale snapshot of a write would be a lie. The store
 * interface has no notion of a pending write, so this is structural, not a rule
 * someone has to remember.
 */
interface CacheStore {

    /** The raw JSON for [key], or `null` when nothing is stored. */
    suspend fun readRaw(key: String): String?

    /** Stores [json] under [key] and stamps the write time. */
    suspend fun writeRaw(key: String, json: String)

    /** When [key] was last written, or `null` when it has never been. */
    suspend fun writtenAt(key: String): Instant?

    /** Drops every snapshot — used on sign-out so no data outlives the session. */
    suspend fun clear()
}

/**
 * The [CacheStore] keys. Centralised so a reader and a writer cannot drift.
 */
object CacheKeys {
    /** The unfiltered first page of orders. */
    const val ORDERS_FIRST_PAGE = "orders_page_0"

    /** The unfiltered first page of the catalogue. */
    const val PRODUCTS_FIRST_PAGE = "products_page_0"
}

/** The single DataStore file. One process-wide instance, as DataStore requires. */
private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "vedant_admin_cache")

/** Builds the app's [CacheStore] over the shared DataStore file. */
fun Context.createCacheStore(): CacheStore = DataStoreCacheStore(cacheDataStore)

/**
 * The real [CacheStore], backed by a preferences DataStore.
 *
 * Each snapshot is a JSON string under `snapshot_<key>`; its write time is a
 * separate long under `snapshot_<key>_at`. A read that fails to parse is logged
 * and treated as "no cache" — a corrupt snapshot degrades to a normal network
 * load rather than crashing the app.
 */
class DataStoreCacheStore(
    private val dataStore: DataStore<Preferences>,
) : CacheStore {

    override suspend fun readRaw(key: String): String? =
        runCatching { dataStore.data.first()[stringPreferencesKey(snapshotKey(key))] }
            .onFailure { AppLog.w(TAG, "cache read failed for '$key'", it) }
            .getOrNull()

    override suspend fun writeRaw(key: String, json: String) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(snapshotKey(key))] = json
                prefs[longPreferencesKey(timeKey(key))] = System.currentTimeMillis()
            }
        }.onFailure { AppLog.w(TAG, "cache write failed for '$key'", it) }
    }

    override suspend fun writtenAt(key: String): Instant? =
        runCatching { dataStore.data.first()[longPreferencesKey(timeKey(key))] }
            .getOrNull()
            ?.let { Instant.fromEpochMilliseconds(it) }

    override suspend fun clear() {
        runCatching { dataStore.edit { it.clear() } }
            .onFailure { AppLog.w(TAG, "cache clear failed", it) }
    }

    private fun snapshotKey(key: String): String = "snapshot_$key"

    private fun timeKey(key: String): String = "snapshot_${key}_at"
}

/** The tolerant JSON codec for snapshots — the same shape the client decodes. */
private val cacheJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Decodes a snapshot, or `null` when it is absent or unreadable. */
suspend fun <T> CacheStore.read(key: String, serializer: KSerializer<T>): T? {
    val raw = readRaw(key) ?: return null
    return runCatching { cacheJson.decodeFromString(serializer, raw) }
        .onFailure { AppLog.w(TAG, "snapshot '$key' could not be decoded", it) }
        .getOrNull()
}

/** Encodes and stores a snapshot. A serialization failure is logged and skipped. */
suspend fun <T> CacheStore.write(key: String, serializer: KSerializer<T>, value: T) {
    val raw = runCatching { cacheJson.encodeToString(serializer, value) }
        .onFailure { AppLog.w(TAG, "snapshot '$key' could not be encoded", it) }
        .getOrNull() ?: return
    writeRaw(key, raw)
}
