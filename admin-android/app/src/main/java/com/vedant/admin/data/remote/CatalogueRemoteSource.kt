package com.vedant.admin.data.remote

import com.vedant.admin.data.remote.dto.CategoryDto
import com.vedant.admin.data.remote.dto.OfferDto
import com.vedant.admin.data.remote.dto.ProductDto
import com.vedant.admin.data.remote.dto.ProductWeightDto
import com.vedant.admin.domain.model.ProductQuery
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The **PostgREST seam** for the catalogue. An interface so every
 * `*RepositoryImpl` can be unit-tested with a fake — live catalogue access is
 * **[BLOCKED]** (no admin creds), so the reads/writes below are proven against a
 * fake, never against the real project.
 *
 * Insert/update use `Returning.Representation` (via `select()`), so PostgREST
 * echoes the **stored** row — including server defaults and generated ids. That
 * echoed row is the only thing a repository maps to the domain; the client never
 * synthesises a post-write state.
 *
 * A method returns `null` when an update/select matched **no** row; the
 * repository turns that into a `NoSuchElementException` → `AppError.Server`.
 */
interface CatalogueRemoteSource {

    // --- products ------------------------------------------------------------

    suspend fun listProducts(query: ProductQuery, from: Long, to: Long): List<ProductDto>

    suspend fun getProduct(productId: String): ProductDto?

    suspend fun insertProduct(fields: ProductDto): ProductDto

    suspend fun updateProduct(productId: String, fields: ProductDto): ProductDto?

    suspend fun setProductAvailability(productId: String, available: Boolean): ProductDto?

    suspend fun setProductActive(productId: String, active: Boolean): ProductDto?

    /**
     * Clears `image_url` with a real SQL `NULL`.
     *
     * A null field in a partial update is **omitted** (`explicitNulls = false`),
     * so "remove the photo" cannot ride on [updateProduct] — it needs PostgREST's
     * `setToNull`, which is what this method emits.
     */
    suspend fun clearProductImage(productId: String): ProductDto?

    suspend fun deleteProduct(productId: String)

    // --- categories ----------------------------------------------------------

    suspend fun listCategories(): List<CategoryDto>

    suspend fun insertCategory(fields: CategoryDto): CategoryDto

    suspend fun updateCategory(categoryId: String, fields: CategoryDto): CategoryDto?

    suspend fun deleteCategory(categoryId: String)

    // --- product_weights -----------------------------------------------------

    suspend fun listWeights(productId: String): List<ProductWeightDto>

    suspend fun insertWeight(fields: ProductWeightDto): ProductWeightDto

    suspend fun updateWeight(weightId: String, fields: ProductWeightDto): ProductWeightDto?

    suspend fun deleteWeight(weightId: String)

    // --- offers --------------------------------------------------------------

    suspend fun listOffers(productId: String?): List<OfferDto>

    suspend fun insertOffer(fields: OfferDto): OfferDto

    suspend fun updateOffer(offerId: String, fields: OfferDto): OfferDto?

    suspend fun deleteOffer(offerId: String)
}

/**
 * The real [CatalogueRemoteSource], backed by PostgREST.
 *
 * ### Tolerant by construction
 * Several catalogue columns are still **UNKNOWN** (BLOCKED — no admin creds), so
 * every select asks for `*` and the client decodes with `ignoreUnknownKeys` (see
 * `SupabaseProvider`). An extra live column is ignored rather than fatal.
 *
 * ### The legacy pairs never appear above this file
 * `base_price`/`price` and `quantity_limit`/`max_order_qty` are written **both**
 * by `ProductDraft.toWriteFields()`, so a partial write is impossible; `icon` is
 * mirrored from `glyph` by `CategoryDraft.toWriteFields()`.
 */
class SupabaseCatalogueRemoteSource(private val client: SupabaseClient) : CatalogueRemoteSource {

    // --- products ------------------------------------------------------------

    override suspend fun listProducts(query: ProductQuery, from: Long, to: Long): List<ProductDto> =
        client.postgrest.from(TABLE_PRODUCTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                query.categoryId?.let { id -> filter { eq("category_id", id) } }
                query.searchTerm?.let { term -> filter { ilike("name", "%$term%") } }
                query.availability?.let { available -> filter { eq("availability", available) } }
                order("name", Order.ASCENDING)
                range(from, to)
            }
            .decodeList()

    override suspend fun getProduct(productId: String): ProductDto? =
        client.postgrest.from(TABLE_PRODUCTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("id", productId) }
                limit(1)
            }
            .decodeList<ProductDto>()
            .firstOrNull()

    override suspend fun insertProduct(fields: ProductDto): ProductDto =
        client.postgrest.from(TABLE_PRODUCTS)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun updateProduct(productId: String, fields: ProductDto): ProductDto? =
        client.postgrest.from(TABLE_PRODUCTS)
            // `id` is never in the SET clause; the pk is the filter.
            .update(fields.copy(id = null)) {
                filter { eq("id", productId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun setProductAvailability(productId: String, available: Boolean): ProductDto? =
        client.postgrest.from(TABLE_PRODUCTS)
            .update(ProductDto(availability = available)) {
                filter { eq("id", productId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun setProductActive(productId: String, active: Boolean): ProductDto? =
        client.postgrest.from(TABLE_PRODUCTS)
            .update(ProductDto(isActive = active)) {
                filter { eq("id", productId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun clearProductImage(productId: String): ProductDto? =
        client.postgrest.from(TABLE_PRODUCTS)
            .update({ setToNull("image_url") }) {
                filter { eq("id", productId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun deleteProduct(productId: String) {
        client.postgrest.from(TABLE_PRODUCTS).delete { filter { eq("id", productId) } }
    }

    // --- categories ----------------------------------------------------------

    override suspend fun listCategories(): List<CategoryDto> =
        client.postgrest.from(TABLE_CATEGORIES)
            .select(columns = Columns.raw(ALL_COLUMNS)) { order("sort_order", Order.ASCENDING) }
            .decodeList()

    override suspend fun insertCategory(fields: CategoryDto): CategoryDto =
        client.postgrest.from(TABLE_CATEGORIES)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun updateCategory(categoryId: String, fields: CategoryDto): CategoryDto? =
        client.postgrest.from(TABLE_CATEGORIES)
            .update(fields.copy(id = null)) {
                filter { eq("id", categoryId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun deleteCategory(categoryId: String) {
        client.postgrest.from(TABLE_CATEGORIES).delete { filter { eq("id", categoryId) } }
    }

    // --- product_weights -----------------------------------------------------

    override suspend fun listWeights(productId: String): List<ProductWeightDto> =
        client.postgrest.from(TABLE_WEIGHTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("product_id", productId) }
                order("grams", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun insertWeight(fields: ProductWeightDto): ProductWeightDto =
        client.postgrest.from(TABLE_WEIGHTS)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun updateWeight(weightId: String, fields: ProductWeightDto): ProductWeightDto? =
        client.postgrest.from(TABLE_WEIGHTS)
            .update(fields.copy(id = null)) {
                filter { eq("id", weightId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun deleteWeight(weightId: String) {
        client.postgrest.from(TABLE_WEIGHTS).delete { filter { eq("id", weightId) } }
    }

    // --- offers --------------------------------------------------------------

    override suspend fun listOffers(productId: String?): List<OfferDto> =
        client.postgrest.from(TABLE_OFFERS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                productId?.let { id -> filter { eq("product_id", id) } }
                order("starts_at", Order.DESCENDING)
            }
            .decodeList()

    override suspend fun insertOffer(fields: OfferDto): OfferDto =
        client.postgrest.from(TABLE_OFFERS)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun updateOffer(offerId: String, fields: OfferDto): OfferDto? =
        client.postgrest.from(TABLE_OFFERS)
            .update(fields.copy(id = null)) {
                filter { eq("id", offerId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun deleteOffer(offerId: String) {
        client.postgrest.from(TABLE_OFFERS).delete { filter { eq("id", offerId) } }
    }

    private companion object {
        const val ALL_COLUMNS = "*"

        const val TABLE_PRODUCTS = "products"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_WEIGHTS = "product_weights"
        const val TABLE_OFFERS = "offers"
    }
}
