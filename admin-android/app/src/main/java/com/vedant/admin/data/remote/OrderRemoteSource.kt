package com.vedant.admin.data.remote

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.data.remote.dto.OrderDto
import com.vedant.admin.data.remote.dto.OrderItemDto
import com.vedant.admin.data.remote.dto.OrderStatusHistoryDto
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.coroutines.CancellationException

private const val TAG = "OrderRemote"

/**
 * The **read** seam over PostgREST for orders. An interface so
 * `OrderRepositoryImpl` can be unit-tested with a fake, without a network or an
 * SDK client (the RLS-hidden `orders` schema is still BLOCKED — no admin creds).
 */
interface OrderRemoteSource {

    /** Newest-first page, filtered server-side. */
    suspend fun listOrders(orderFilter: OrderFilter, page: Int, pageSize: Int): List<OrderDto>

    /** One order with its items and history, or `null` when it does not exist. */
    suspend fun getOrder(orderId: String): OrderDto?

    /** The status timeline for one order. */
    suspend fun statusHistory(orderId: String): List<OrderStatusHistoryDto>

    /** The order that owns an item — needed to re-fetch after a reject/restore. */
    suspend fun orderIdForItem(itemId: String): String?

    /** Today's counts keyed by the **raw** status string. */
    suspend fun statusCountsToday(): Map<String, Int>
}

/**
 * PostgREST-backed reads.
 *
 * ### Tolerant by construction
 * `orders`/`order_items`/`order_status_history` are RLS-hidden and their exact
 * columns are **BLOCKED**, so every select declares only columns we have
 * evidence for and the client decodes with `ignoreUnknownKeys` (see
 * `SupabaseProvider`). The `profiles` embed is attempted best-effort: if the
 * `orders → profiles` relationship does not exist, PostgREST answers `PGRST2xx`
 * and we transparently retry without it rather than failing the screen.
 */
class SupabaseOrderRemoteSource(private val client: SupabaseClient) : OrderRemoteSource {

    override suspend fun listOrders(orderFilter: OrderFilter, page: Int, pageSize: Int): List<OrderDto> {
        val from = page.coerceAtLeast(0).toLong() * pageSize
        val to = from + pageSize - 1

        return withProfileFallback(
            primary = {
                client.postgrest.from(TABLE_ORDERS)
                    .select(columns = Columns.raw(LIST_SELECT)) { applyListRequest(orderFilter, from, to) }
                    .decodeList()
            },
            fallback = {
                client.postgrest.from(TABLE_ORDERS)
                    .select(columns = Columns.raw(LIST_SELECT_NO_PROFILE)) { applyListRequest(orderFilter, from, to) }
                    .decodeList()
            },
        )
    }

    override suspend fun getOrder(orderId: String): OrderDto? = withProfileFallback(
        primary = {
            client.postgrest.from(TABLE_ORDERS)
                .select(columns = Columns.raw(DETAIL_SELECT)) { filter { eq("id", orderId) } }
                .decodeList<OrderDto>()
                .firstOrNull()
        },
        fallback = {
            client.postgrest.from(TABLE_ORDERS)
                .select(columns = Columns.raw(DETAIL_SELECT_NO_PROFILE)) { filter { eq("id", orderId) } }
                .decodeList<OrderDto>()
                .firstOrNull()
        },
    )

    override suspend fun statusHistory(orderId: String): List<OrderStatusHistoryDto> =
        client.postgrest.from(TABLE_HISTORY)
            .select(columns = Columns.raw(HISTORY_SELECT)) {
                filter { eq("order_id", orderId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun orderIdForItem(itemId: String): String? =
        client.postgrest.from(TABLE_ITEMS)
            .select(columns = Columns.list("order_id")) {
                filter { eq("id", itemId) }
                limit(1)
            }
            .decodeList<OrderItemDto>()
            .firstOrNull()
            ?.orderId

    override suspend fun statusCountsToday(): Map<String, Int> {
        // The lower bound of "today" in the store's own zone, as a UTC instant —
        // "today" for the owner means IST, not UTC.
        val since = AppClock.startOfTodayIstUtc().toString()
        val counts = LinkedHashMap<String, Int>(OrderStatus.entries.size)
        OrderStatus.entries.forEach { status ->
            val count = client.postgrest.from(TABLE_ORDERS)
                .select(columns = Columns.list("id")) {
                    count(Count.EXACT)
                    filter {
                        eq("status", status.name)
                        gte("placed_at", since)
                    }
                    // One row is enough: the exact count arrives in Content-Range.
                    limit(1)
                }
                .countOrNull() ?: 0L
            counts[status.name] = count.toInt()
        }
        return counts
    }

    // --- Request helpers ----------------------------------------------------

    private fun PostgrestRequestBuilder.applyListRequest(
        orderFilter: OrderFilter,
        from: Long,
        to: Long,
    ) {
        orderFilter.status?.let { status -> filter { eq("status", status.name) } }
        // `request_id` is the human, text order handle (the RPC takes
        // `p_request_id text`); searching the uuid `id` would need a cast.
        orderFilter.searchTerm?.let { term -> filter { ilike("request_id", "%$term%") } }
        // `orders` has no `created_at`; the placed timestamp is `placed_at`
        // (verified live 2026-10-16). Ordering by `created_at` returned 42703.
        order("placed_at", Order.DESCENDING)
        range(from, to)
    }

    /**
     * Runs [primary]; if PostgREST rejects the `profiles` embed specifically
     * (`PGRST2xx` — no/ambiguous relationship), retries with [fallback].
     *
     * Any other failure (network, `42501`) propagates unchanged — this must not
     * mask a real error or loop on it.
     */
    private suspend fun <T> withProfileFallback(primary: suspend () -> T, fallback: suspend () -> T): T =
        try {
            primary()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            if (rest.error?.startsWith("PGRST2") == true) {
                AppLog.w(TAG, "profiles embed unavailable (${rest.error}); retrying without it")
                fallback()
            } else {
                throw rest
            }
        }

    private companion object {
        const val TABLE_ORDERS = "orders"
        const val TABLE_ITEMS = "order_items"
        const val TABLE_HISTORY = "order_status_history"

        const val PROFILE_EMBED = "profiles(id, first_name, last_name, username, email)"

        const val LIST_SELECT = "*, order_items(id), $PROFILE_EMBED"
        const val LIST_SELECT_NO_PROFILE = "*, order_items(id)"

        const val DETAIL_SELECT = "*, order_items(*), order_status_history(*), $PROFILE_EMBED"
        const val DETAIL_SELECT_NO_PROFILE = "*, order_items(*), order_status_history(*)"

        const val HISTORY_SELECT = "*"
    }
}
