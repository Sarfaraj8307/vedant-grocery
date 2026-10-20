package com.vedant.admin.testing

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.domain.repository.OfferRepository
import com.vedant.admin.domain.repository.ProductRepository
import com.vedant.admin.domain.repository.SlotRepository
import com.vedant.admin.domain.repository.WeightRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Instant

/**
 * Test data and fakes for the Phase-3 catalogue + slots ViewModel tests.
 *
 * The fakes let a test drive every read and write to a chosen `Outcome`, and —
 * via a [CompletableDeferred] gate — hold a write open so the in-flight state is
 * observable. That is what makes "a double-submit is blocked" and "a rejected
 * write leaves the rendered data untouched" assertable without a device, a
 * network, or admin credentials (the live catalogue/storage path stays
 * **[BLOCKED]**, R1).
 */

private const val DAY_MS = 86_400_000L

/** A UTC instant offset by whole days — avoids any `Duration` import ambiguity. */
fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + days * DAY_MS)

// --- Builders ---------------------------------------------------------------

fun testProduct(
    id: String = "p1",
    name: String = "Tomatoes",
    slug: String? = "tomatoes",
    categoryId: String? = "c1",
    description: String? = null,
    basePrice: Money = Money(4_000),
    discountPrice: Money? = null,
    hasOffer: Boolean = false,
    isActive: Boolean = true,
    isFeatured: Boolean = false,
    stock: Int? = 50,
    quantityLimit: Int? = 5,
    unit: String? = "kg",
    unitLabel: String? = "kg",
    availability: Boolean = true,
    imageUrl: String? = null,
    glyph: String? = "\uD83C\uDF45",
    accent: Int? = null,
    createdAt: Instant? = null,
    updatedAt: Instant? = null,
): Product = Product(
    id = id,
    name = name,
    slug = slug,
    categoryId = categoryId,
    description = description,
    basePrice = basePrice,
    discountPrice = discountPrice,
    hasOffer = hasOffer,
    isActive = isActive,
    isFeatured = isFeatured,
    stock = stock,
    quantityLimit = quantityLimit,
    unit = unit,
    unitLabel = unitLabel,
    availability = availability,
    imageUrl = imageUrl,
    glyph = glyph,
    accent = accent,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testCategory(
    id: String = "c1",
    name: String = "Vegetables",
    slug: String? = "vegetables",
    glyph: String? = "\uD83E\uDD66",
    accent: Int? = null,
    imageUrl: String? = null,
    sortOrder: Int = 0,
    isActive: Boolean = true,
    createdAt: Instant? = null,
    updatedAt: Instant? = null,
): Category = Category(
    id = id,
    name = name,
    slug = slug,
    glyph = glyph,
    accent = accent,
    imageUrl = imageUrl,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testWeight(
    id: String = "w1",
    productId: String = "p1",
    label: String = "500 g",
    grams: Int? = 500,
    price: Money = Money(4_000),
    isAvailable: Boolean = true,
    createdAt: Instant? = null,
    updatedAt: Instant? = null,
): ProductWeight = ProductWeight(
    id = id,
    productId = productId,
    label = label,
    grams = grams,
    price = price,
    isAvailable = isAvailable,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testOffer(
    id: String = "of1",
    productId: String = "p1",
    title: String = "Diwali special",
    price: Money? = null,
    discountPercent: Int? = 20,
    startsAt: Instant? = null,
    endsAt: Instant? = null,
    isActive: Boolean = true,
    createdAt: Instant? = null,
    updatedAt: Instant? = null,
): Offer = Offer(
    id = id,
    productId = productId,
    title = title,
    price = price,
    discountPercent = discountPercent,
    startsAt = startsAt,
    endsAt = endsAt,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** `2026-10-11T02:30:00Z` is `08:00` IST — the Morning window's start. */
val TEST_SLOT_START: Instant = Instant.parse("2026-10-11T02:30:00Z")

/** `2026-10-11T04:30:00Z` is `10:00` IST — the Morning window's end. */
val TEST_SLOT_END: Instant = Instant.parse("2026-10-11T04:30:00Z")

fun testSlot(
    id: String = "s1",
    name: String? = "Morning",
    startsAt: Instant = TEST_SLOT_START,
    endsAt: Instant = TEST_SLOT_END,
    capacity: Int = 25,
    sortOrder: Int = 0,
    isActive: Boolean = true,
    createdAt: Instant? = null,
    updatedAt: Instant? = null,
): DeliverySlot = DeliverySlot(
    id = id,
    name = name,
    startsAt = startsAt,
    endsAt = endsAt,
    capacity = capacity,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testSlotLoad(
    slotId: String = "s1",
    name: String? = "Morning",
    startsAt: Instant? = TEST_SLOT_START,
    endsAt: Instant? = TEST_SLOT_END,
    capacity: Int = 25,
    booked: Int = 18,
): SlotLoad = SlotLoad(
    slotId = slotId,
    name = name,
    startsAt = startsAt,
    endsAt = endsAt,
    capacity = capacity,
    booked = booked,
)

// --- Fakes ------------------------------------------------------------------

/** A scriptable [ProductRepository]. */
class FakeProductRepository : ProductRepository {

    /** One `Outcome` per page index; a missing index yields an empty page. */
    var listResults: List<Outcome<List<Product>>> = emptyList()
    var getResult: Outcome<Product> = Outcome.Success(testProduct())
    var createResult: Outcome<Product> = Outcome.Success(testProduct())
    var updateResult: Outcome<Product> = Outcome.Success(testProduct())
    var setAvailabilityResult: Outcome<Product> = Outcome.Success(testProduct(availability = false))
    var setActiveResult: Outcome<Product> = Outcome.Success(testProduct())
    var clearImageResult: Outcome<Product> = Outcome.Success(testProduct(imageUrl = null))
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)
    var uploadResult: Outcome<String> = Outcome.Success("https://cdn.example/products/p1/x.jpg")

    /** Progress values the fake reports while "uploading". */
    var uploadProgress: List<Float> = listOf(0.25f, 0.6f, 1f)

    val listRequests = mutableListOf<Pair<ProductQuery, Int>>()
    var getCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var setAvailabilityCalls = 0
    var clearImageCalls = 0
    var uploadCalls = 0
    var lastUploadedBytes: ByteArray? = null
    var lastUploadedMime: String? = null
    var lastWrittenDraft: ProductDraft? = null

    /** When non-null, `setAvailability` suspends on it — holds a toggle "in flight". */
    var availabilityGate: CompletableDeferred<Unit>? = null

    override suspend fun listProducts(
        query: ProductQuery,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Product>> {
        listRequests += query to page
        return listResults.getOrElse(page) { Outcome.Success(emptyList()) }
    }

    override suspend fun getProduct(productId: String): Outcome<Product> {
        getCalls++
        return getResult
    }

    override suspend fun createProduct(draft: ProductDraft): Outcome<Product> {
        createCalls++
        lastWrittenDraft = draft
        return createResult
    }

    override suspend fun updateProduct(draft: ProductDraft): Outcome<Product> {
        updateCalls++
        lastWrittenDraft = draft
        return updateResult
    }

    override suspend fun setAvailability(productId: String, available: Boolean): Outcome<Product> {
        setAvailabilityCalls++
        availabilityGate?.await()
        return setAvailabilityResult
    }

    override suspend fun setActive(productId: String, active: Boolean): Outcome<Product> = setActiveResult

    override suspend fun clearImage(productId: String): Outcome<Product> {
        clearImageCalls++
        return clearImageResult
    }

    override suspend fun deleteProduct(productId: String): Outcome<Unit> = deleteResult

    override suspend fun uploadImage(
        productId: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<String> {
        uploadCalls++
        lastUploadedBytes = bytes
        lastUploadedMime = mimeType
        uploadProgress.forEach(onProgress)
        return uploadResult
    }
}

/** A scriptable [CategoryRepository]. */
class FakeCategoryRepository : CategoryRepository {

    var listResult: Outcome<List<Category>> = Outcome.Success(emptyList())
    var createResult: Outcome<Category> = Outcome.Success(testCategory())
    var updateResult: Outcome<Category> = Outcome.Success(testCategory())
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)

    var listCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var lastWrittenDraft: CategoryDraft? = null

    override suspend fun listCategories(): Outcome<List<Category>> {
        listCalls++
        return listResult
    }

    override suspend fun createCategory(draft: CategoryDraft): Outcome<Category> {
        createCalls++
        lastWrittenDraft = draft
        return createResult
    }

    override suspend fun updateCategory(draft: CategoryDraft): Outcome<Category> {
        updateCalls++
        lastWrittenDraft = draft
        return updateResult
    }

    override suspend fun deleteCategory(categoryId: String): Outcome<Unit> {
        deleteCalls++
        return deleteResult
    }
}

/** A scriptable [WeightRepository]. */
class FakeWeightRepository : WeightRepository {

    var listResult: Outcome<List<ProductWeight>> = Outcome.Success(emptyList())
    var saveResult: Outcome<ProductWeight> = Outcome.Success(testWeight())
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)

    var listCalls = 0
    var saveCalls = 0
    var deleteCalls = 0
    var lastWrittenDraft: ProductWeightDraft? = null

    override suspend fun listWeights(productId: String): Outcome<List<ProductWeight>> {
        listCalls++
        return listResult
    }

    override suspend fun saveWeight(draft: ProductWeightDraft): Outcome<ProductWeight> {
        saveCalls++
        lastWrittenDraft = draft
        return saveResult
    }

    override suspend fun deleteWeight(weightId: String): Outcome<Unit> {
        deleteCalls++
        return deleteResult
    }
}

/** A scriptable [OfferRepository]. */
class FakeOfferRepository : OfferRepository {

    var listResult: Outcome<List<Offer>> = Outcome.Success(emptyList())
    var createResult: Outcome<Offer> = Outcome.Success(testOffer())
    var updateResult: Outcome<Offer> = Outcome.Success(testOffer())
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)

    val listRequests = mutableListOf<String?>()
    var createCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var lastWrittenDraft: OfferDraft? = null

    override suspend fun listOffers(productId: String?): Outcome<List<Offer>> {
        listRequests += productId
        return listResult
    }

    override suspend fun createOffer(draft: OfferDraft): Outcome<Offer> {
        createCalls++
        lastWrittenDraft = draft
        return createResult
    }

    override suspend fun updateOffer(draft: OfferDraft): Outcome<Offer> {
        updateCalls++
        lastWrittenDraft = draft
        return updateResult
    }

    override suspend fun deleteOffer(offerId: String): Outcome<Unit> {
        deleteCalls++
        return deleteResult
    }
}

/** A scriptable [SlotRepository]. */
class FakeSlotRepository : SlotRepository {

    var listResult: Outcome<List<DeliverySlot>> = Outcome.Success(emptyList())
    var loadResult: Outcome<List<SlotLoad>> = Outcome.Success(emptyList())
    var ensureResult: Outcome<Unit> = Outcome.Success(Unit)
    var saveResult: Outcome<DeliverySlot> = Outcome.Success(testSlot())
    var deleteResult: Outcome<Unit> = Outcome.Success(Unit)

    val listRequests = mutableListOf<Pair<Instant, Int>>()
    var loadCalls = 0
    var ensureCalls = 0
    var saveCalls = 0
    var deleteCalls = 0
    var lastWrittenDraft: DeliverySlotDraft? = null

    override suspend fun listSlots(from: Instant, days: Int): Outcome<List<DeliverySlot>> {
        listRequests += from to days
        return listResult
    }

    override suspend fun slotLoad(from: Instant, days: Int): Outcome<List<SlotLoad>> {
        loadCalls++
        return loadResult
    }

    override suspend fun ensureSlots(from: Instant, days: Int): Outcome<Unit> {
        ensureCalls++
        return ensureResult
    }

    override suspend fun saveSlot(draft: DeliverySlotDraft): Outcome<DeliverySlot> {
        saveCalls++
        lastWrittenDraft = draft
        return saveResult
    }

    override suspend fun deleteSlot(slotId: String): Outcome<Unit> {
        deleteCalls++
        return deleteResult
    }
}
