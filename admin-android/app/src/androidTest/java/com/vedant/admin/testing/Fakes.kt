package com.vedant.admin.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.AdminIdentity
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.DashboardCounts
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderItem
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.model.OrderSummary
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.domain.repository.ComplaintRepository
import com.vedant.admin.domain.repository.OfferRepository
import com.vedant.admin.domain.repository.OrderRepository
import com.vedant.admin.domain.repository.ProductRepository
import com.vedant.admin.domain.repository.SlotRepository
import com.vedant.admin.domain.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * Test data and fake repositories for the **instrumented** (Compose) suite.
 *
 * The instrumented source set cannot see `app/src/test`, so these builders and
 * fakes are deliberately self-contained. Every fake drives a screen to a chosen
 * `Outcome` with no network, no device credentials and no live Supabase — the
 * live path stays **BLOCKED (R1)**; these tests prove the *rendering contract*.
 */

// --- Domain builders --------------------------------------------------------

private val FIXED_INSTANT: Instant = Instant.parse("2026-10-11T10:00:00Z")

fun testItem(
    id: String = "i1",
    productName: String = "Tomatoes",
    weightLabel: String? = "1 kg",
    qty: Int = 2,
    unitPrice: Money = Money(4_000),
    lineTotal: Money = Money(8_000),
    rejected: Boolean = false,
    rejectionReason: String? = null,
): OrderItem = OrderItem(
    id = id,
    productId = "p1",
    productName = productName,
    weightLabel = weightLabel,
    qty = qty,
    unitPrice = unitPrice,
    lineTotal = lineTotal,
    rejected = rejected,
    rejectionReason = rejectionReason,
)

fun testOrder(
    id: String = "o1",
    status: OrderStatus? = OrderStatus.PENDING,
    statusRaw: String = status?.name ?: "PENDING",
    originalTotal: Money = Money(15_200),
    adjustmentAmount: Money = Money.ZERO,
    finalTotal: Money = Money(15_200),
    deliveryCharge: Money = Money(4_000),
    items: List<OrderItem> = listOf(testItem()),
    history: List<OrderStatusEvent> = emptyList(),
): Order = Order(
    id = id,
    status = status,
    statusRaw = statusRaw,
    originalTotal = originalTotal,
    adjustmentAmount = adjustmentAmount,
    finalTotal = finalTotal,
    deliveryCharge = deliveryCharge,
    subtotal = null,
    deliverySlotId = "slot-1",
    customerRef = "cust-1",
    customerName = "Asha",
    addressLines = listOf("12 Market Road", "Pune 411001"),
    adjustmentReason = null,
    adjustedAt = null,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
    items = items,
    history = history,
)

fun testSummary(
    id: String = "o1",
    status: OrderStatus? = OrderStatus.PENDING,
    finalTotal: Money = Money(15_200),
): OrderSummary = OrderSummary(
    id = id,
    status = status,
    statusRaw = status?.name ?: "PENDING",
    customerRef = "cust-1",
    customerName = "Asha",
    createdAt = FIXED_INSTANT,
    itemCount = 1,
    finalTotal = finalTotal,
)

fun testProduct(
    id: String = "p1",
    name: String = "Tomatoes",
    basePrice: Money = Money(4_000),
    availability: Boolean = true,
    isActive: Boolean = true,
    categoryId: String? = "c1",
): Product = Product(
    id = id,
    name = name,
    slug = "tomatoes",
    categoryId = categoryId,
    description = "Farm fresh",
    basePrice = basePrice,
    discountPrice = null,
    hasOffer = false,
    isActive = isActive,
    isFeatured = false,
    stock = 10,
    quantityLimit = 5,
    unit = "kg",
    unitLabel = "kg",
    availability = availability,
    imageUrl = null,
    glyph = "\uD83C\uDF45",
    accent = null,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testCategory(id: String = "c1", name: String = "Vegetables"): Category = Category(
    id = id,
    name = name,
    slug = "vegetables",
    glyph = "\uD83E\uDD66",
    accent = null,
    imageUrl = null,
    sortOrder = 0,
    isActive = true,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testWeight(id: String = "w1", productId: String = "p1"): ProductWeight = ProductWeight(
    id = id,
    productId = productId,
    label = "1 kg",
    grams = 1000,
    price = Money(4_000),
    isAvailable = true,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testOffer(id: String = "of1", productId: String = "p1"): Offer = Offer(
    id = id,
    productId = productId,
    title = "Festive 10%",
    price = null,
    discountPercent = 10,
    startsAt = null,
    endsAt = null,
    isActive = true,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testSlot(id: String = "s1"): DeliverySlot = DeliverySlot(
    id = id,
    name = "Morning",
    startsAt = FIXED_INSTANT,
    endsAt = FIXED_INSTANT,
    capacity = 20,
    sortOrder = 0,
    isActive = true,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testComplaint(
    id: String = "complaint-1",
    subject: String = "Missing item",
    status: ComplaintStatus? = ComplaintStatus.OPEN,
    statusRaw: String = status?.name ?: "OPEN",
): Complaint = Complaint(
    id = id,
    orderId = "order-12345678",
    customerRef = "cust-1",
    customerName = "Asha",
    subject = subject,
    status = status,
    statusRaw = statusRaw,
    type = null,
    typeRaw = "MISSING_ITEM",
    createdAt = FIXED_INSTANT,
    updatedAt = null,
)

fun testMessage(id: String = "m1", body: String = "Where is my order?"): ComplaintMessage =
    ComplaintMessage(
        id = id,
        complaintId = "complaint-1",
        authorRole = null,
        authorRoleRaw = "CUSTOMER",
        authorId = "cust-1",
        body = body,
        createdAt = FIXED_INSTANT,
    )

// --- Fakes ------------------------------------------------------------------

/** A scriptable [OrderRepository] for the instrumented tests. */
class FakeOrderRepository : OrderRepository {
    var getOrderResult: Outcome<Order> = Outcome.Success(testOrder())
    var updateStatusResult: Outcome<Order> = Outcome.Success(testOrder(status = OrderStatus.CONFIRMED))
    var rejectResult: Outcome<Order> = Outcome.Success(testOrder())
    var restoreResult: Outcome<Order> = Outcome.Success(testOrder())

    var listResult: Outcome<List<OrderSummary>> = Outcome.Success(listOf(testSummary()))

    var updateStatusCalls = 0

    override suspend fun listOrders(
        filter: OrderFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<OrderSummary>> = listResult

    override suspend fun getOrder(orderId: String): Outcome<Order> = getOrderResult

    override suspend fun updateStatus(
        orderId: String,
        status: OrderStatus,
        reason: String?,
    ): Outcome<Order> {
        updateStatusCalls++
        return updateStatusResult
    }

    override suspend fun rejectItem(itemId: String, reason: String): Outcome<Order> = rejectResult

    override suspend fun restoreItem(itemId: String): Outcome<Order> = restoreResult

    override suspend fun statusHistory(orderId: String): Outcome<List<OrderStatusEvent>> =
        Outcome.Success(emptyList())

    override suspend fun dashboardCounts(): Outcome<DashboardCounts> =
        Outcome.Success(DashboardCounts.EMPTY)
}

/** A scriptable [ProductRepository] for the instrumented tests. */
class FakeProductRepository : ProductRepository {
    var listResult: Outcome<List<Product>> = Outcome.Success(listOf(testProduct()))
    var getResult: Outcome<Product> = Outcome.Success(testProduct())
    var createResult: Outcome<Product> = Outcome.Success(testProduct())
    var updateResult: Outcome<Product> = Outcome.Success(testProduct())
    var availabilityResult: Outcome<Product> = Outcome.Success(testProduct())

    override suspend fun listProducts(
        query: ProductQuery,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Product>> = listResult

    override suspend fun getProduct(productId: String): Outcome<Product> = getResult

    override suspend fun createProduct(draft: ProductDraft): Outcome<Product> = createResult

    override suspend fun updateProduct(draft: ProductDraft): Outcome<Product> = updateResult

    override suspend fun setAvailability(productId: String, available: Boolean): Outcome<Product> =
        availabilityResult

    override suspend fun setActive(productId: String, active: Boolean): Outcome<Product> =
        availabilityResult

    override suspend fun clearImage(productId: String): Outcome<Product> = updateResult

    override suspend fun deleteProduct(productId: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun uploadImage(
        productId: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<String> = Outcome.Success("https://cdn.example.com/p1.jpg")
}

/** A scriptable [CategoryRepository] for the instrumented tests. */
class FakeCategoryRepository : CategoryRepository {
    var listResult: Outcome<List<Category>> = Outcome.Success(listOf(testCategory()))

    override suspend fun listCategories(): Outcome<List<Category>> = listResult

    override suspend fun createCategory(
        draft: com.vedant.admin.domain.model.CategoryDraft,
    ): Outcome<Category> = Outcome.Success(testCategory())

    override suspend fun updateCategory(
        draft: com.vedant.admin.domain.model.CategoryDraft,
    ): Outcome<Category> = Outcome.Success(testCategory())

    override suspend fun deleteCategory(categoryId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** A scriptable [WeightRepository] for the instrumented tests. */
class FakeWeightRepository : WeightRepository {
    var listResult: Outcome<List<ProductWeight>> = Outcome.Success(listOf(testWeight()))

    override suspend fun listWeights(productId: String): Outcome<List<ProductWeight>> = listResult

    override suspend fun saveWeight(draft: ProductWeightDraft): Outcome<ProductWeight> =
        Outcome.Success(testWeight())

    override suspend fun deleteWeight(weightId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** A scriptable [OfferRepository] for the instrumented tests. */
class FakeOfferRepository : OfferRepository {
    var listResult: Outcome<List<Offer>> = Outcome.Success(listOf(testOffer()))

    override suspend fun listOffers(productId: String?): Outcome<List<Offer>> = listResult

    override suspend fun createOffer(draft: OfferDraft): Outcome<Offer> =
        Outcome.Success(testOffer())

    override suspend fun updateOffer(draft: OfferDraft): Outcome<Offer> =
        Outcome.Success(testOffer())

    override suspend fun deleteOffer(offerId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** A scriptable [SlotRepository] for the instrumented tests. */
class FakeSlotRepository : SlotRepository {
    var listResult: Outcome<List<DeliverySlot>> = Outcome.Success(listOf(testSlot()))
    var loadResult: Outcome<List<SlotLoad>> = Outcome.Success(emptyList())
    var ensureResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun listSlots(from: Instant, days: Int): Outcome<List<DeliverySlot>> =
        listResult

    override suspend fun slotLoad(from: Instant, days: Int): Outcome<List<SlotLoad>> = loadResult

    override suspend fun ensureSlots(from: Instant, days: Int): Outcome<Unit> = ensureResult

    override suspend fun saveSlot(draft: DeliverySlotDraft): Outcome<DeliverySlot> =
        Outcome.Success(testSlot())

    override suspend fun deleteSlot(slotId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** A scriptable [ComplaintRepository] for the instrumented tests. */
class FakeComplaintRepository : ComplaintRepository {
    var listResult: Outcome<List<Complaint>> = Outcome.Success(listOf(testComplaint()))

    override suspend fun listComplaints(
        filter: ComplaintFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Complaint>> = listResult

    override suspend fun getComplaint(complaintId: String): Outcome<Complaint> =
        Outcome.Success(testComplaint())

    override suspend fun listMessages(complaintId: String): Outcome<List<ComplaintMessage>> =
        Outcome.Success(listOf(testMessage()))

    override suspend fun changeStatus(
        complaintId: String,
        status: ComplaintStatus,
    ): Outcome<Complaint> = Outcome.Success(testComplaint(status = status))

    override suspend fun reply(complaintId: String, body: String): Outcome<ComplaintMessage> =
        Outcome.Success(testMessage(body = body))

    override suspend fun listAttachments(
        complaintId: String,
    ): Outcome<List<ComplaintAttachment>> = Outcome.Success(emptyList())

    override suspend fun uploadAttachment(
        complaintId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<ComplaintAttachment> = Outcome.Success(
        ComplaintAttachment(
            id = "a1",
            complaintId = complaintId,
            storagePath = "cust-1/$fileName",
            fileName = fileName,
            url = null,
            createdAt = FIXED_INSTANT,
        ),
    )
}

/** A scriptable [AuthRepository] for the instrumented tests. */
class FakeAuthRepository : AuthRepository {
    override val sessionState: StateFlow<SessionState> =
        MutableStateFlow(SessionState.SignedOut)

    var signInResult: Outcome<Unit> = Outcome.Success(Unit)
    var isAdminResult: Outcome<Boolean> = Outcome.Success(true)

    override suspend fun signIn(email: String, password: String): Outcome<Unit> = signInResult

    override suspend fun signOut(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun restoreSession(): Outcome<AdminIdentity> =
        Outcome.Success(AdminIdentity(userId = "u1", email = "owner@vedant.test", isAdmin = true))

    override suspend fun isAdmin(): Outcome<Boolean> = isAdminResult

    override suspend fun currentUserId(): String? = "u1"

    override suspend fun currentAccessToken(): String? = "token"
}

// --- ViewModel factory ------------------------------------------------------

/**
 * A minimal [ViewModelProvider.Factory] that returns a ViewModel built from a
 * fake. Compose's `viewModel(factory = …)` caches the instance per
 * ViewModelStoreOwner, so the screen is driven by exactly the fake the test set up.
 */
fun <T : ViewModel> fakeViewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
