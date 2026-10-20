package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.StorageDataSource
import com.vedant.admin.data.remote.dto.CategoryDto
import com.vedant.admin.data.remote.dto.OfferDto
import com.vedant.admin.data.remote.dto.ProductDto
import com.vedant.admin.data.remote.dto.ProductWeightDto
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.model.ProductWeightDraft
import com.vedant.admin.domain.repository.ProductRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T19 acceptance, unit-tested with a **fake** PostgREST (`FakeCatalogueRemote`)
 * and a fake upload seam (`FakeStorage`). Live catalogue access and the live
 * `product-images` bucket are **[BLOCKED]** (no admin creds — R1), so these
 * tests prove the *repository contract*:
 *
 *  - every write returns the **server-confirmed row**, never the draft;
 *  - a create writes **both members of both legacy pairs** and mirrors
 *    `icon := glyph`;
 *  - an update that matches no row is a `Failure`, not a silent success;
 *  - an oversize/ill-typed image is refused **before** a byte is sent;
 *  - the upload returns the URL derived from the path the server stored.
 */
class CatalogueRepositoryImplTest {

    private val remote = FakeCatalogueRemote()
    private val storage = FakeStorage()
    private val products = ProductRepositoryImpl(remote = remote, storage = storage)
    private val categories = CategoryRepositoryImpl(remote)
    private val weights = WeightRepositoryImpl(remote)
    private val offers = OfferRepositoryImpl(remote)

    private fun productDto(
        id: String? = "p1",
        name: String? = "Tomatoes",
        availability: Boolean? = true,
    ) = ProductDto(id = id, name = name, availability = availability)

    // --- products -----------------------------------------------------------

    @Test
    fun `createProduct writes both members of both legacy pairs`() = runTest {
        val draft = ProductDraft(name = "Tomatoes", basePrice = Money(4_000), quantityLimit = 5)

        val result = products.createProduct(draft)

        assertTrue(result is Outcome.Success)
        val sent = remote.insertedProducts.single()
        assertEquals(Money(4_000), sent.basePrice)
        assertEquals(sent.basePrice, sent.price) // pair 1: base_price == price
        assertEquals(5, sent.quantityLimit)
        assertEquals(sent.quantityLimit, sent.maxOrderQty) // pair 2: quantity_limit == max_order_qty
    }

    @Test
    fun `createProduct returns the row the server stored`() = runTest {
        val result = products.createProduct(ProductDraft(name = "Okra", basePrice = Money(3_000)))

        assertTrue(result is Outcome.Success)
        assertEquals("new-product", (result as Outcome.Success).value.id)
    }

    @Test
    fun `updateProduct is a Failure when the row no longer exists`() = runTest {
        remote.updateProductMissing = true

        val result = products.updateProduct(ProductDraft(id = "p1", name = "Tomatoes", basePrice = Money(4_000)))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Server)
    }

    @Test
    fun `updateProduct refuses a draft with no id before touching the server`() = runTest {
        val result = products.updateProduct(ProductDraft(name = "Tomatoes", basePrice = Money(4_000)))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        assertTrue(remote.updatedProducts.isEmpty())
    }

    @Test
    fun `setAvailability returns the re-read server row`() = runTest {
        remote.availabilityResult = productDto(availability = false)

        val result = products.setAvailability("p1", false)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value.availability)
        assertEquals(listOf("p1" to false), remote.availabilityCalls)
    }

    @Test
    fun `getProduct is a Failure when the product is missing`() = runTest {
        remote.productById = null

        val result = products.getProduct("missing")

        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun `listProducts maps rows to the domain`() = runTest {
        remote.listProductsResult = listOf(productDto(id = "p1"), productDto(id = "p2"))

        val result = products.listProducts(ProductQuery.NONE, page = 0)

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `clearImage uses the explicit-null path, not a partial update`() = runTest {
        remote.clearImageResult = productDto()

        val result = products.clearImage("p1")

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("p1"), remote.clearedImages)
        assertTrue("must not go through a partial update", remote.updatedProducts.isEmpty())
    }

    // --- image upload -------------------------------------------------------

    @Test
    fun `uploadImage refuses an oversize payload without touching storage`() = runTest {
        val oversize = ByteArray(ProductRepository.MAX_IMAGE_BYTES + 1)

        val result = products.uploadImage("p1", oversize, "image/png")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        assertTrue("no byte may be sent", storage.uploads.isEmpty())
    }

    @Test
    fun `uploadImage refuses a disallowed content type`() = runTest {
        val result = products.uploadImage("p1", ByteArray(10) { 1 }, "image/gif")

        assertTrue(result is Outcome.Failure)
        assertTrue(storage.uploads.isEmpty())
    }

    @Test
    fun `uploadImage returns the server-confirmed URL and reports progress`() = runTest {
        val progress = mutableListOf<Float>()

        val result = products.uploadImage("p1", ByteArray(10) { 1 }, "image/jpeg") { progress += it }

        assertTrue(result is Outcome.Success)
        val url = (result as Outcome.Success).value
        assertTrue("URL must be the bucket's public URL", url.startsWith("https://cdn.test/product-images/"))
        assertTrue("path must be namespaced by product", url.contains("/products/p1/"))
        assertEquals("image/jpeg", storage.uploads.single().third)
        assertTrue("progress must be reported", progress.isNotEmpty())
    }

    // --- categories ---------------------------------------------------------

    @Test
    fun `createCategory mirrors icon from glyph`() = runTest {
        val result = categories.createCategory(CategoryDraft(name = "Fruits", glyph = "\uD83C\uDF4E"))

        assertTrue(result is Outcome.Success)
        val sent = remote.insertedCategories.single()
        assertEquals("\uD83C\uDF4E", sent.glyph)
        assertEquals(sent.glyph, sent.icon) // pair 5: icon := glyph, never the reverse
    }

    @Test
    fun `updateCategory is a Failure when the row no longer exists`() = runTest {
        remote.updateCategoryMissing = true

        val result = categories.updateCategory(CategoryDraft(id = "c1", name = "Fruits"))

        assertTrue(result is Outcome.Failure)
    }

    // --- weights ------------------------------------------------------------

    @Test
    fun `saveWeight inserts when the draft has no id`() = runTest {
        val result = weights.saveWeight(
            ProductWeightDraft(productId = "p1", label = "1 kg", grams = 1000, price = Money(4_000)),
        )

        assertTrue(result is Outcome.Success)
        assertEquals("new-weight", (result as Outcome.Success).value.id)
        assertEquals(1, remote.insertedWeights.size)
        assertTrue(remote.updatedWeights.isEmpty())
    }

    @Test
    fun `saveWeight updates when the draft has an id`() = runTest {
        val result = weights.saveWeight(
            ProductWeightDraft(id = "w1", productId = "p1", label = "500 g", grams = 500, price = Money(2_000)),
        )

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("w1"), remote.updatedWeights.map { it.first })
        assertTrue(remote.insertedWeights.isEmpty())
    }

    @Test
    fun `saveWeight is a Failure when an updated variant is gone`() = runTest {
        remote.updateWeightMissing = true

        val result = weights.saveWeight(
            ProductWeightDraft(id = "w1", productId = "p1", label = "500 g", price = Money(2_000)),
        )

        assertTrue(result is Outcome.Failure)
    }

    // --- offers -------------------------------------------------------------

    @Test
    fun `createOffer rejects an out-of-range discount without a remote call`() = runTest {
        val result = offers.createOffer(OfferDraft(productId = "p1", title = "Deal", discountPercent = 150))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        assertTrue(remote.insertedOffers.isEmpty())
    }

    @Test
    fun `createOffer rejects an inverted window`() = runTest {
        val result = offers.createOffer(
            OfferDraft(
                productId = "p1",
                title = "Deal",
                discountPercent = 10,
                startsAt = Instant.parse("2026-10-12T00:00:00Z"),
                endsAt = Instant.parse("2026-10-11T00:00:00Z"),
            ),
        )

        assertTrue(result is Outcome.Failure)
        assertTrue(remote.insertedOffers.isEmpty())
    }

    @Test
    fun `createOffer returns the stored row for a valid offer`() = runTest {
        val result = offers.createOffer(OfferDraft(productId = "p1", title = "Deal", discountPercent = 20))

        assertTrue(result is Outcome.Success)
        assertEquals("new-offer", (result as Outcome.Success).value.id)
        assertNull(remote.insertedOffers.single().id) // create sends no id
    }

    // --- fakes --------------------------------------------------------------

    private class FakeCatalogueRemote : CatalogueRemoteSource {

        var listProductsResult: List<ProductDto> = emptyList()
        var productById: ProductDto? = null
        var updateProductMissing = false

        var availabilityResult: ProductDto? = null
        var activeResult: ProductDto? = null
        var clearImageResult: ProductDto? = null

        val insertedProducts = mutableListOf<ProductDto>()
        val updatedProducts = mutableListOf<Pair<String, ProductDto>>()
        val availabilityCalls = mutableListOf<Pair<String, Boolean>>()
        val activeCalls = mutableListOf<Pair<String, Boolean>>()
        val clearedImages = mutableListOf<String>()
        val deletedProducts = mutableListOf<String>()

        var categoryList: List<CategoryDto> = emptyList()
        var updateCategoryMissing = false
        val insertedCategories = mutableListOf<CategoryDto>()
        val updatedCategories = mutableListOf<Pair<String, CategoryDto>>()

        var weightList: List<ProductWeightDto> = emptyList()
        var updateWeightMissing = false
        val insertedWeights = mutableListOf<ProductWeightDto>()
        val updatedWeights = mutableListOf<Pair<String, ProductWeightDto>>()

        var offerList: List<OfferDto> = emptyList()
        var updateOfferMissing = false
        val insertedOffers = mutableListOf<OfferDto>()
        val updatedOffers = mutableListOf<Pair<String, OfferDto>>()

        override suspend fun listProducts(query: ProductQuery, from: Long, to: Long): List<ProductDto> =
            listProductsResult

        override suspend fun getProduct(productId: String): ProductDto? = productById

        override suspend fun insertProduct(fields: ProductDto): ProductDto {
            insertedProducts += fields
            return fields.copy(id = "new-product")
        }

        override suspend fun updateProduct(productId: String, fields: ProductDto): ProductDto? {
            updatedProducts += productId to fields
            return if (updateProductMissing) null else fields.copy(id = productId)
        }

        override suspend fun setProductAvailability(productId: String, available: Boolean): ProductDto? {
            availabilityCalls += productId to available
            return availabilityResult
        }

        override suspend fun setProductActive(productId: String, active: Boolean): ProductDto? {
            activeCalls += productId to active
            return activeResult
        }

        override suspend fun clearProductImage(productId: String): ProductDto? {
            clearedImages += productId
            return clearImageResult
        }

        override suspend fun deleteProduct(productId: String) {
            deletedProducts += productId
        }

        override suspend fun listCategories(): List<CategoryDto> = categoryList

        override suspend fun insertCategory(fields: CategoryDto): CategoryDto {
            insertedCategories += fields
            return fields.copy(id = "new-category")
        }

        override suspend fun updateCategory(categoryId: String, fields: CategoryDto): CategoryDto? {
            updatedCategories += categoryId to fields
            return if (updateCategoryMissing) null else fields.copy(id = categoryId)
        }

        override suspend fun deleteCategory(categoryId: String) = Unit

        override suspend fun listWeights(productId: String): List<ProductWeightDto> = weightList

        override suspend fun insertWeight(fields: ProductWeightDto): ProductWeightDto {
            insertedWeights += fields
            return fields.copy(id = "new-weight")
        }

        override suspend fun updateWeight(weightId: String, fields: ProductWeightDto): ProductWeightDto? {
            updatedWeights += weightId to fields
            return if (updateWeightMissing) null else fields.copy(id = weightId)
        }

        override suspend fun deleteWeight(weightId: String) = Unit

        override suspend fun listOffers(productId: String?): List<OfferDto> = offerList

        override suspend fun insertOffer(fields: OfferDto): OfferDto {
            insertedOffers += fields
            return fields.copy(id = "new-offer")
        }

        override suspend fun updateOffer(offerId: String, fields: OfferDto): OfferDto? {
            updatedOffers += offerId to fields
            return if (updateOfferMissing) null else fields.copy(id = offerId)
        }

        override suspend fun deleteOffer(offerId: String) = Unit
    }

    private class FakeStorage : StorageDataSource {
        val uploads = mutableListOf<Triple<String, ByteArray, String>>()
        var failure: Throwable? = null

        override suspend fun uploadProductImage(
            path: String,
            bytes: ByteArray,
            mimeType: String,
            onProgress: (Float) -> Unit,
        ): String {
            failure?.let { throw it }
            uploads += Triple(path, bytes, mimeType)
            onProgress(1f)
            return "https://cdn.test/product-images/$path"
        }

        // The private complaint bucket is not exercised by the catalogue tests;
        // the members exist only so this fake satisfies the storage seam.
        override suspend fun uploadComplaintAttachment(
            path: String,
            bytes: ByteArray,
            mimeType: String,
            onProgress: (Float) -> Unit,
        ): String {
            failure?.let { throw it }
            onProgress(1f)
            return path
        }

        override suspend fun signedComplaintUrl(path: String, expiresInSeconds: Long): String? = null
    }
}
