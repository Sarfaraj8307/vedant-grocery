package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The row written to `delivery_issues` when a courier reports a problem (§25,
 * migration `20261016_016_delivery_issues.sql`).
 *
 * Only the three fields the client owns are sent: `reported_by` and
 * `created_at` are server defaults (`auth.uid()` / `now()`), so a tampered
 * client cannot forge the actor, and `id` is a server default too. RLS
 * re-checks everything server-side regardless — this shape exists so a
 * well-formed client request is possible, not as the enforcement point.
 */
@Serializable
data class DeliveryIssueInsertDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("issue_type") val issueType: String,
    @SerialName("reason") val reason: String,
)
