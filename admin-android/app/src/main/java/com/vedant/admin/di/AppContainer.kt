package com.vedant.admin.di

import android.app.Application
import com.vedant.admin.core.network.AndroidConnectivityObserver
import com.vedant.admin.core.network.ConnectivityObserver
import com.vedant.admin.core.network.ConnectionMonitor
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.local.AppearancePreferences
import com.vedant.admin.data.local.CacheStore
import com.vedant.admin.data.local.ReadRetryScheduler
import com.vedant.admin.data.local.SettingsAppearancePreferences
import com.vedant.admin.data.local.WorkManagerReadRetryScheduler
import com.vedant.admin.data.local.createCacheStore
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.ComplaintRemoteSource
import com.vedant.admin.data.remote.OrderRemoteSource
import com.vedant.admin.data.remote.SettingsRemoteSource
import com.vedant.admin.data.remote.SlotRemoteSource
import com.vedant.admin.data.remote.StorageDataSource
import com.vedant.admin.data.remote.SupabaseAdminRpc
import com.vedant.admin.data.remote.SupabaseCatalogueRemoteSource
import com.vedant.admin.data.remote.SupabaseComplaintRemoteSource
import com.vedant.admin.data.remote.SupabaseOrderRemoteSource
import com.vedant.admin.data.remote.SupabaseProvider
import com.vedant.admin.data.remote.SupabaseSettingsRemoteSource
import com.vedant.admin.data.remote.SupabaseSlotRemoteSource
import com.vedant.admin.data.remote.SupabaseStorageDataSource
import com.vedant.admin.data.repository.AuthRepositoryImpl
import com.vedant.admin.data.repository.AppearanceRepositoryImpl
import com.vedant.admin.data.repository.CategoryRepositoryImpl
import com.vedant.admin.data.repository.ComplaintRepositoryImpl
import com.vedant.admin.data.repository.OfferRepositoryImpl
import com.vedant.admin.data.repository.OrderRepositoryImpl
import com.vedant.admin.data.repository.ProductRepositoryImpl
import com.vedant.admin.data.repository.ProfileRepositoryImpl
import com.vedant.admin.data.repository.SlotRepositoryImpl
import com.vedant.admin.data.repository.StoreSettingsRepositoryImpl
import com.vedant.admin.data.repository.WeightRepositoryImpl
import com.vedant.admin.data.session.SessionStore
import com.vedant.admin.data.session.SupabaseSessionStore
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.repository.AppearanceRepository
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.domain.repository.ComplaintRepository
import com.vedant.admin.domain.repository.OfferRepository
import com.vedant.admin.domain.repository.OrderRepository
import com.vedant.admin.domain.repository.ProductRepository
import com.vedant.admin.domain.repository.ProfileRepository
import com.vedant.admin.domain.repository.SlotRepository
import com.vedant.admin.domain.repository.StoreSettingsRepository
import com.vedant.admin.domain.repository.WeightRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.russhwolf.settings.Settings

/**
 * Hand-rolled dependency container — **no Hilt, no KSP** (explicit brief +
 * `CLAUDE.md`). Created once, in `VedantAdminApplication.onCreate()`.
 *
 * Everything is `by lazy`, so a screen that only needs the auth repository never
 * pays for anything else. The `SupabaseClient` is created here and handed only to
 * the data layer; no ViewModel or composable ever sees it.
 *
 * When Hilt is eventually allowed, this file is the single seam to replace.
 */
class AppContainer(val app: Application) {

    /** The one Supabase client for the process. Publishable key only. */
    val supabase: SupabaseClient by lazy { SupabaseProvider.create() }

    val sessionStore: SessionStore by lazy { SupabaseSessionStore(supabase) }

    val adminRpc: AdminRpc by lazy { SupabaseAdminRpc(supabase) }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(sessionStore = sessionStore, adminRpc = adminRpc)
    }

    /** The PostgREST reads for orders (list/detail/history/counts). */
    val orderRemoteSource: OrderRemoteSource by lazy { SupabaseOrderRemoteSource(supabase) }

    /** Order reads plus the three admin mutations, with an offline first-page cache. */
    val orderRepository: OrderRepository by lazy {
        OrderRepositoryImpl(
            remote = orderRemoteSource,
            adminRpc = adminRpc,
            cache = cacheStore,
            retry = readRetryScheduler,
        )
    }

    // --- Catalogue (Phase 3) ------------------------------------------------

    /** PostgREST reads/writes for products, categories, weights and offers. */
    val catalogueRemoteSource: CatalogueRemoteSource by lazy { SupabaseCatalogueRemoteSource(supabase) }

    /** Uploads to the public `product-images` bucket, with progress. */
    val storageDataSource: StorageDataSource by lazy { SupabaseStorageDataSource(supabase) }

    /** Catalogue products, with image upload and an offline first-page cache. */
    val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(
            remote = catalogueRemoteSource,
            storage = storageDataSource,
            cache = cacheStore,
            retry = readRetryScheduler,
        )
    }

    val categoryRepository: CategoryRepository by lazy { CategoryRepositoryImpl(catalogueRemoteSource) }

    val weightRepository: WeightRepository by lazy { WeightRepositoryImpl(catalogueRemoteSource) }

    val offerRepository: OfferRepository by lazy { OfferRepositoryImpl(catalogueRemoteSource) }

    // --- Delivery slots (Phase 3) -------------------------------------------

    /** PostgREST reads/writes for `delivery_slots`. */
    val slotRemoteSource: SlotRemoteSource by lazy { SupabaseSlotRemoteSource(supabase) }

    /** Slots, their utilisation and the two slot RPCs. */
    val slotRepository: SlotRepository by lazy {
        SlotRepositoryImpl(remote = slotRemoteSource, adminRpc = adminRpc)
    }

    // --- Support / complaints (Phase 4) -------------------------------------

    /** PostgREST reads/writes for `complaints`, `complaint_messages` and `complaint_attachments`. */
    val complaintRemoteSource: ComplaintRemoteSource by lazy { SupabaseComplaintRemoteSource(supabase) }

    /** Complaints, their threads and private `complaint-images` attachments. */
    val complaintRepository: ComplaintRepository by lazy {
        ComplaintRepositoryImpl(
            remote = complaintRemoteSource,
            storage = storageDataSource,
            auth = authRepository,
        )
    }

    // --- Settings & profile (Phase 4) ---------------------------------------

    /** PostgREST reads/writes for `store_settings`, plus the `profiles` read. */
    val settingsRemoteSource: SettingsRemoteSource by lazy { SupabaseSettingsRemoteSource(supabase) }

    /** The store's single-row configuration. */
    val storeSettingsRepository: StoreSettingsRepository by lazy {
        StoreSettingsRepositoryImpl(remote = settingsRemoteSource)
    }

    /** The operator's identity, with the server-resolved admin role. */
    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(remote = settingsRemoteSource, auth = authRepository)
    }

    // --- Offline cache, connectivity & read retry (Phase 5, T24) ------------

    /**
     * App-scoped: lives as long as the process. Used for the connectivity
     * observer's eager flow and for the cache-refresh work.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** JSON snapshots of the last-good reads (DataStore — deliberately not Room). */
    val cacheStore: CacheStore by lazy { app.createCacheStore() }

    /**
     * The persisted appearance choice (§8). Uses the same
     * `multiplatform-settings` mechanism as the session — no new dependency.
     */
    val appearancePreferences: AppearancePreferences by lazy {
        SettingsAppearancePreferences(Settings())
    }

    /** The System/Light/Dark choice, seeded synchronously for the first frame. */
    val appearanceRepository: AppearanceRepository by lazy {
        AppearanceRepositoryImpl(appearancePreferences)
    }

    /** Whether the device currently has a network — drives the offline banner. */
    val connectivity: ConnectivityObserver by lazy { AndroidConnectivityObserver(app, appScope) }

    /**
     * The five explicit connection states (§52), derived from [connectivity]
     * plus the outcome of real requests.
     *
     * This is what screens should read for *why* the app is blocked. Raw
     * connectivity alone cannot tell "no network" apart from "network present,
     * backend unreachable" — which is the distinction that used to make a
     * backend failure read as "offline".
     */
    val connectionMonitor: ConnectionMonitor by lazy { ConnectionMonitor(connectivity, appScope) }

    /** Retries **safe reads only** via WorkManager; never a mutation. */
    val readRetryScheduler: ReadRetryScheduler by lazy { WorkManagerReadRetryScheduler(app) }

    /**
     * Re-runs the app's **safe reads** and refreshes the cache. Called by
     * `SafeReadWorker` once a network is available again.
     *
     * It touches **reads only** — there is no code path here that could replay a
     * queued write. Returns `true` when every read succeeded.
     */
    suspend fun refreshCachedReads(): Boolean {
        val orders = orderRepository.listOrders(OrderFilter.NONE, page = 0)
        val products = productRepository.listProducts(ProductQuery.NONE, page = 0)
        return orders is Outcome.Success && products is Outcome.Success
    }

    /** Drops the cache and any pending retry — used on sign-out. */
    suspend fun clearOfflineState() {
        readRetryScheduler.cancel()
        cacheStore.clear()
    }

    // This container only ever references types that are real.
}
