package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.data.local.CacheKeys
import com.vedant.admin.data.local.CacheStore
import com.vedant.admin.data.local.ReadRetryScheduler
import com.vedant.admin.data.local.read
import com.vedant.admin.data.local.write
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.StorageDataSource
import com.vedant.admin.data.remote.dto.ProductDto
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.repository.ProductRepository
import kotlinx.serialization.builtins.ListSerializer

private const val TAG = "ProductRepository"

/** The snapshot serializer for a page of raw product rows. */
private val PRODUCT_PAGE_SERIALIZER = ListSerializer(ProductDto.serializer())

/**
 * The real [ProductRepository].
 *
 * It composes two replaceable collaborators:
 *  - [CatalogueRemoteSource] — the PostgREST **reads/writes** (`products`).
 *  - [StorageDataSource]     — the **upload** seam for `product-images`.
 *
 * ### Never fake success
 * A write does not return the draft: it sends the row and returns the row
 * **PostgREST echoed** (`Returning.Representation`), i.e. what the server
 * actually stored. If the request throws (network, `42501`, a constraint) the
 * exception propagates as `Outcome.Failure` and nothing is mapped — so the
 * ViewModel has no new state and the UI does not move.
 *
 * ### Offline (T24)
 * The **unfiltered first page** is cached and used only for a **network** failure
 * on that page; the offline banner says the rows are saved. No write is ever
 * cached or queued.
 */
class ProductRepositoryImpl(
    private val remote: CatalogueRemoteSource,
    private val storage: StorageDataSource,
    private val cache: CacheStore? = null,
    private val retry: ReadRetryScheduler? = null,
) : ProductRepository {

    override suspend fun listProducts(
        query: ProductQuery,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Product>> {
        val from = page.coerceAtLeast(0).toLong() * pageSize
        val outcome = outcomeOf { remote.listProducts(query, from, from + pageSize - 1) }
        return when (outcome) {
            is Outcome.Success -> {
                cacheFirstPage(query, page, outcome.value)
                Outcome.Success(outcome.value.map { it.toDomain() })
            }

            is Outcome.Failure -> {
                val cached = cachedFirstPage(query, page, outcome.error)
                if (cached != null) Outcome.Success(cached.map { it.toDomain() }) else outcome
            }
        }
    }

    /** Stores the unfiltered first page — the one a cold start would show. */
    private suspend fun cacheFirstPage(query: ProductQuery, page: Int, rows: List<ProductDto>) {
        val store = cache ?: return
        if (page != 0 || query.isActive) return
        store.write(CacheKeys.PRODUCTS_FIRST_PAGE, PRODUCT_PAGE_SERIALIZER, rows)
    }

    /** The offline fallback: only for a **network** failure on the unfiltered first page. */
    private suspend fun cachedFirstPage(
        query: ProductQuery,
        page: Int,
        error: AppError,
    ): List<ProductDto>? {
        if (error != AppError.Network || page != 0 || query.isActive) return null
        retry?.scheduleSafeReadRetry()
        return cache?.read(CacheKeys.PRODUCTS_FIRST_PAGE, PRODUCT_PAGE_SERIALIZER)
    }

    override suspend fun getProduct(productId: String): Outcome<Product> = outcomeOf {
        require(productId.isNotBlank()) { "A product id is required." }
        remote.getProduct(productId)?.toDomain() ?: throw notFound(productId)
    }

    override suspend fun createProduct(draft: ProductDraft): Outcome<Product> = outcomeOf {
        require(draft.isCreate) { "This product already exists." }
        require(draft.name.isNotBlank()) { "A product name is required." }
        AppLog.i(TAG, "createProduct name=${draft.name}")
        // toWriteFields() writes both members of both legacy pairs (§10).
        remote.insertProduct(draft.toWriteFields()).toDomain()
    }

    override suspend fun updateProduct(draft: ProductDraft): Outcome<Product> = outcomeOf {
        val id = draft.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A product id is required.")
        require(draft.name.isNotBlank()) { "A product name is required." }
        AppLog.i(TAG, "updateProduct id=$id")
        remote.updateProduct(id, draft.toWriteFields())?.toDomain() ?: throw notFound(id)
    }

    override suspend fun setAvailability(productId: String, available: Boolean): Outcome<Product> = outcomeOf {
        remote.setProductAvailability(productId, available)?.toDomain() ?: throw notFound(productId)
    }

    override suspend fun setActive(productId: String, active: Boolean): Outcome<Product> = outcomeOf {
        remote.setProductActive(productId, active)?.toDomain() ?: throw notFound(productId)
    }

    override suspend fun clearImage(productId: String): Outcome<Product> = outcomeOf {
        remote.clearProductImage(productId)?.toDomain() ?: throw notFound(productId)
    }

    override suspend fun deleteProduct(productId: String): Outcome<Unit> = outcomeOf {
        remote.deleteProduct(productId)
    }

    override suspend fun uploadImage(
        productId: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<String> = outcomeOf {
        validateImage(bytes, mimeType)
        storage.uploadProductImage(
            path = imagePath(productId, mimeType),
            bytes = bytes,
            mimeType = mimeType,
            onProgress = onProgress,
        )
    }

    /** Rejects a payload the bucket would reject anyway, before it leaves the device. */
    private fun validateImage(bytes: ByteArray, mimeType: String) {
        require(bytes.isNotEmpty()) { "The selected image is empty." }
        require(bytes.size <= ProductRepository.MAX_IMAGE_BYTES) {
            "Images must be 5 MB or smaller."
        }
        require(mimeType.lowercase() in ProductRepository.ALLOWED_IMAGE_MIME) {
            "Images must be JPEG, PNG or WebP."
        }
    }

    /** A collision-free object path under `products/<id>/`. */
    private fun imagePath(productId: String, mimeType: String): String {
        val safeId = productId.takeIf { it.isNotBlank() } ?: UNASSIGNED
        val stamp = AppClock.now().toEpochMilliseconds()
        return "products/$safeId/$stamp.${extensionFor(mimeType)}"
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "bin"
    }

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Product $id no longer exists.")

    private companion object {
        const val UNASSIGNED = "unassigned"
    }
}
