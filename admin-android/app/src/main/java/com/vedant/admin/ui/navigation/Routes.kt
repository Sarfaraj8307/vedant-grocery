package com.vedant.admin.ui.navigation

import com.vedant.admin.domain.model.OrderStatus

/**
 * Every route string in the app, in one place.
 *
 * Argument keys are declared here too, so a destination and its caller cannot
 * drift apart. Route *patterns* (the ones registered on the `NavHost`) and the
 * builder functions (the ones a caller uses to navigate) sit side by side, so a
 * typo in a segment shows up as a compile error at the call site rather than as a
 * silent "destination not found" at runtime.
 */
object Routes {

    /** Entry point. Session restore happens here; the gate routes onward. */
    const val SPLASH = "splash"

    /** Email + password. */
    const val SIGN_IN = "signin"

    /** Signed in, but the server says not an admin. A dead end with sign-out. */
    const val NOT_AUTHORIZED = "not_authorized"

    /** Landing screen for a confirmed admin. */
    const val DASHBOARD = "dashboard"

    // --- Orders -------------------------------------------------------------

    /** Optional query arg that seeds the list's status filter. */
    const val ARG_STATUS = "status"

    /** The list, without a filter (the bare route a caller may navigate to). */
    const val ORDERS = "orders"

    /** The registered list pattern, with its optional filter. */
    const val ORDERS_ROUTE = "orders?$ARG_STATUS={$ARG_STATUS}"

    /** The path arg naming the order to show. */
    const val ARG_ORDER_ID = "orderId"

    /** The registered order-detail pattern. */
    const val ORDER_DETAIL_ROUTE = "orders/{$ARG_ORDER_ID}"

    /** The courier's assigned-deliveries queue (§21). */
    const val DELIVERY = "delivery"

    /** Support / complaints list. */
    const val SUPPORT = "support"

    /** The path arg naming the complaint to open. */
    const val ARG_COMPLAINT_ID = "complaintId"

    /** The registered complaint-thread pattern. */
    const val COMPLAINT_THREAD_ROUTE = "support/{$ARG_COMPLAINT_ID}"

    // --- Catalogue ----------------------------------------------------------

    /** The path arg naming the product to edit. */
    const val ARG_PRODUCT_ID = "productId"

    /** The literal used in the editor route to mean "create a new product". */
    const val NEW_PRODUCT = "new"

    /** The catalogue hub (product list). */
    const val CATALOGUE = "catalogue"

    /** Category management. */
    const val CATEGORIES = "catalogue/categories"

    /** The registered product-editor pattern; `productId == NEW_PRODUCT` creates. */
    const val PRODUCT_EDITOR_ROUTE = "catalogue/product/{$ARG_PRODUCT_ID}"

    /** Weight variants for one product. */
    const val WEIGHTS_ROUTE = "catalogue/product/{$ARG_PRODUCT_ID}/weights"

    /** Offers for one product. */
    const val OFFERS_ROUTE = "catalogue/product/{$ARG_PRODUCT_ID}/offers"

    /** Delivery slots. */
    const val SLOTS = "slots"

    // --- Settings (Phase 4) -------------------------------------------------

    /** The store's single-row configuration. */
    const val STORE_SETTINGS = "settings/store"

    /** The signed-in operator's identity and server-resolved role. */
    const val PROFILE = "settings/profile"

    /** The device-side System/Light/Dark choice (§8). */
    const val APPEARANCE = "settings/appearance"

    /** Navigate to the list, optionally pre-filtered by [status]. */
    fun orders(status: OrderStatus? = null): String =
        if (status == null) ORDERS else "$ORDERS?$ARG_STATUS=${status.name}"

    /** Navigate to one order's detail. */
    fun orderDetail(orderId: String): String = "$ORDERS/$orderId"

    /** Navigate to the editor; `null` creates a new product. */
    fun productEditor(productId: String?): String =
        "catalogue/product/${productId?.takeIf { it.isNotBlank() } ?: NEW_PRODUCT}"

    /** Navigate to one product's weight variants. */
    fun weights(productId: String): String = "catalogue/product/$productId/weights"

    /** Navigate to one product's offers. */
    fun offers(productId: String): String = "catalogue/product/$productId/offers"

    /** Navigate to one complaint's thread. */
    fun complaintThread(complaintId: String): String = "$SUPPORT/$complaintId"
}
