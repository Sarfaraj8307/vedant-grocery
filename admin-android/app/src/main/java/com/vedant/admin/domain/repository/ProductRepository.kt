package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductQuery

/**
 * Catalogue reads and writes for products.
 *
 * ### Never fake success
 * Every write returns the **server-confirmed row** (`Outcome<Product>`), never
 * the draft that was sent. The ViewModel therefore has nothing to render until
 * the server echoes the stored row, and a rejection surfaces as
 * `Outcome.Failure` while the UI stays exactly where it was.
 *
 * The §10 legacy-pair expansion (`base_price`/`price`, `quantity_limit`/
 * `max_order_qty`) happens **only** in `data/remote/mapper/CatalogueMapper`, so
 * no name in this contract carries a legacy column.
 */
interface ProductRepository {

    /** One page of products matching [query], filtered and ordered server-side. */
    suspend fun listProducts(
        query: ProductQuery = ProductQuery.NONE,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Outcome<List<Product>>

    /** The full product, or a `Failure` when it no longer exists. */
    suspend fun getProduct(productId: String): Outcome<Product>

    /** Inserts a new product and returns the row the server stored. */
    suspend fun createProduct(draft: ProductDraft): Outcome<Product>

    /** Updates an existing product and returns the row the server stored. */
    suspend fun updateProduct(draft: ProductDraft): Outcome<Product>

    /** Quick availability toggle from the hub. Returns the re-read row. */
    suspend fun setAvailability(productId: String, available: Boolean): Outcome<Product>

    /** Hide/show from the hub. Returns the re-read row. */
    suspend fun setActive(productId: String, active: Boolean): Outcome<Product>

    /** Removes a product's image (a real SQL `NULL`). Returns the re-read row. */
    suspend fun clearImage(productId: String): Outcome<Product>

    /** Removes a product. A missing row is not an error. */
    suspend fun deleteProduct(productId: String): Outcome<Unit>

    /**
     * Uploads a product image and returns the **server-confirmed public URL**.
     *
     * [onProgress] is called with `0f..1f` while the bytes are in flight, so the
     * editor can show real progress rather than a spinner. The returned URL is
     * not "set" until the caller persists it on the product and that write
     * returns — a successful local file pick is never success on its own.
     *
     * The payload is validated against the bucket's own limits
     * ([MAX_IMAGE_BYTES], [ALLOWED_IMAGE_MIME]) before a single byte is sent.
     */
    suspend fun uploadImage(
        productId: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit = {},
    ): Outcome<String>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        /** The `product-images` bucket limit (5 MB). */
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

        /** The content types the `product-images` bucket accepts. */
        val ALLOWED_IMAGE_MIME: Set<String> = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
        )
    }
}
