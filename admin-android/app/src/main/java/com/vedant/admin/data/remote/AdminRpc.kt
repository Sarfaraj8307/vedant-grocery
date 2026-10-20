package com.vedant.admin.data.remote

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
}
