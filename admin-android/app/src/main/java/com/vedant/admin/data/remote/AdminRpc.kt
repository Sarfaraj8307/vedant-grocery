package com.vedant.admin.data.remote

import com.vedant.admin.data.remote.dto.DeliveryIssueInsertDto
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typed wrapper over the admin RPCs. The domain/repository layer depends on this
 * interface, never on the SDK, so authorization logic and the order lifecycle
 * are unit-testable with a fake.
 *
 * Every call here is **server-authoritative**: the RPC is `SECURITY DEFINER`,
 * re-checks `private.is_admin()` internally, re-validates the transition, and
 * recomputes any money. The client sends **no amount** (CLAUDE.md rule 5) — the
 * caller re-fetches the row afterwards to see the server's result.
 */
interface AdminRpc {

    /**
     * The **only** authorization question the client ever asks: is the current
     * session an admin, according to the server?
     *
     * Fails **closed**: a response that cannot be decoded as `true` yields
     * `false`, so an ambiguous answer never grants admin. Genuine failures
     * (network, `42501`) still throw and are surfaced as `AppError` by the caller.
     */
    suspend fun isAdmin(): Boolean

    /**
     * `admin_update_order_status(p_order_id, p_status, p_reason)`.
     *
     * Returns nothing: the caller re-fetches the order so the UI only ever shows
     * the server's post-transition row (never a locally computed one).
     */
    suspend fun updateOrderStatus(orderId: String, status: String, reason: String?)

    /** `admin_reject_order_item(p_item_id, p_reason)` — server recomputes totals. */
    suspend fun rejectOrderItem(itemId: String, reason: String)

    /** `admin_restore_order_item(p_item_id)` — server recomputes totals. */
    suspend fun restoreOrderItem(itemId: String)

    /**
     * `ensure_delivery_slots(p_from, p_days)` — materialises the recurring slot
     * rows for the window. Idempotent server-side; the caller re-reads afterwards.
     */
    suspend fun ensureDeliverySlots(from: String, days: Int)

    /**
     * `admin_slot_load(p_from, p_days)` — booked-vs-capacity per slot.
     *
     * The result shape is **BLOCKED** (no admin creds — R1); the rows are decoded
     * into [SlotLoadDto], which declares every plausible alias, and normalised by
     * `SlotMapper`.
     */
    suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto>

    /**
     * `delivery_orders(p_only_mine)` — the courier's queue (§21).
     *
     * Server-authoritative: the RPC narrows to the caller's own assignments when
     * their role is `DELIVERY`, and refuses entirely for roles without order
     * management (`42501`). Passing `onlyMine = false` lets a dispatcher
     * (OWNER/ADMIN/MANAGER) see the whole active queue; the server ignores the
     * flag for a `DELIVERY` caller, so a tampered client cannot widen its view.
     */
    suspend fun deliveryOrders(onlyMine: Boolean = true): List<DeliveryOrderDto>

    /**
     * Whether the store is currently taking new orders (§9).
     *
     * Reads `store_settings.is_open` — the same column `place_order` enforces.
     * Returns `null` when it cannot be read, so the UI can say "unknown" instead
     * of assuming "open" and letting an operator believe orders are live.
     */
    suspend fun isAcceptingOrders(): Boolean?

    /**
     * `admin_set_order_acceptance(p_accepting, p_reason)` — stop or resume trade.
     *
     * Server-authoritative: gated on `private.can_manage_store()`, requires a
     * reason when closing, and writes `store_status_history` + `audit_logs`.
     * The caller re-reads afterwards rather than assuming the new state.
     */
    suspend fun setOrderAcceptance(accepting: Boolean, reason: String?)

    /**
     * Reports a delivery problem on [orderId] — the courier's REPORT ISSUE
     * action (§25, migration `20261016_016_delivery_issues.sql`).
     *
     * This is a plain table insert, not an RPC: authorization lives in the
     * table's RLS. A DELIVERY user may only insert for orders assigned to
     * them; OWNER/ADMIN for any order; every other role (and anon/customers)
     * is refused with `42501`. The actor is taken from the session by the
     * server (`reported_by default auth.uid()`) — this client cannot forge it.
     *
     * [type] is a [com.vedant.admin.domain.model.DeliveryIssueType.wire] value
     * and [reason] is required by both the client-side validation and a server
     * CHECK, so a report is never persisted without specifics. The log is
     * append-only: there is no update or retract path, by design.
     *
     * Throws on refusal (network error, or `42501` when the row is not the
     * caller's assignment) — the caller surfaces it, never assumes success.
     */
    suspend fun reportDeliveryIssue(orderId: String, type: String, reason: String)

}

/** The real implementation, backed by the `public.admin_*` RPCs. */
class SupabaseAdminRpc(private val client: SupabaseClient) : AdminRpc {

    override suspend fun isAdmin(): Boolean {
        val result = client.postgrest.rpc("is_admin")
        return result.decodeAsOrNull<Boolean>() ?: false
    }

    override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) {
        client.postgrest.rpc(
            function = "admin_update_order_status",
            parameters = buildJsonObject {
                put("p_order_id", orderId)
                put("p_status", status)
                // Null is a valid reason for a non-cancelling transition; the
                // server enforces that CANCELLED requires one.
                put("p_reason", reason)
            },
        )
    }

    override suspend fun rejectOrderItem(itemId: String, reason: String) {
        client.postgrest.rpc(
            function = "admin_reject_order_item",
            parameters = buildJsonObject {
                put("p_item_id", itemId)
                put("p_reason", reason)
            },
        )
    }

    override suspend fun restoreOrderItem(itemId: String) {
        client.postgrest.rpc(
            function = "admin_restore_order_item",
            parameters = buildJsonObject {
                put("p_item_id", itemId)
            },
        )
    }

    override suspend fun ensureDeliverySlots(from: String, days: Int) {
        client.postgrest.rpc(
            function = "ensure_delivery_slots",
            parameters = buildJsonObject {
                put("p_from", from)
                put("p_days", days)
            },
        )
    }

    override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> =
        client.postgrest.rpc(
            function = "admin_slot_load",
            parameters = buildJsonObject {
                put("p_from", from)
                put("p_days", days)
            },
        ).decodeList()

    override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> =
        client.postgrest.rpc(
            function = "delivery_orders",
            parameters = buildJsonObject {
                put("p_only_mine", onlyMine)
            },
        ).decodeList()


    override suspend fun isAcceptingOrders(): Boolean? = runCatching {
        client.postgrest.from("store_settings")
            .select(io.github.jan.supabase.postgrest.query.Columns.list("is_open")) {
                filter { eq("id", 1) }
            }
            .decodeSingleOrNull<StoreOpenDto>()
            ?.isOpen
    }.getOrNull()

    override suspend fun setOrderAcceptance(accepting: Boolean, reason: String?) {
        client.postgrest.rpc(
            function = "admin_set_order_acceptance",
            parameters = buildJsonObject {
                put("p_accepting", accepting)
                put("p_reason", reason)
            },
        )
    }

    override suspend fun reportDeliveryIssue(orderId: String, type: String, reason: String) {
        client.postgrest.from(TABLE_DELIVERY_ISSUES)
            .insert(DeliveryIssueInsertDto(orderId = orderId, issueType = type, reason = reason))
    }

}

/** The append-only delivery escalation log (§25). */
private const val TABLE_DELIVERY_ISSUES = "delivery_issues"


/** Minimal projection of `store_settings` for the acceptance read. */
@kotlinx.serialization.Serializable
private data class StoreOpenDto(
    @kotlinx.serialization.SerialName("is_open") val isOpen: Boolean? = null,
)
