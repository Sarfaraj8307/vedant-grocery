package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `complaints` row.
 *
 * **Column availability: BLOCKED (no admin creds — R1).** `complaints` is
 * RLS-hidden, so its live columns could not be enumerated (`docs/CONTRACT.md`
 * §10). This DTO therefore declares every plausible name for the fields the
 * architecture specifies, makes them all nullable except `id`, and — combined
 * with supabase-kt's `ignoreUnknownKeys` — decodes a renamed or absent column as
 * `null` instead of throwing.
 *
 * `status` and `type` are declared as `String` (not enums) on purpose: an enum
 * would make an unknown wire value a decode failure. `SupportMapper` parses them
 * tolerantly and keeps the raw string.
 *
 * Re-check this DTO after the T02 probe enumerates the real columns.
 */
@Serializable
data class ComplaintDto(
    val id: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val subject: String? = null,
    val description: String? = null,
    val message: String? = null,
    val status: String? = null,
    val type: String? = null,
    @SerialName("complaint_type") val complaintType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * The **write** payload for a status change.
 *
 * A dedicated one-field DTO rather than a [ComplaintDto] with everything nulled:
 * the update can then only ever touch `status`, so a partial write cannot clobber
 * a column the operator did not edit. The response is decoded back into
 * [ComplaintDto] (the update payload type and the response type need not match).
 */
@Serializable
data class ComplaintStatusUpdate(
    val status: String,
)
