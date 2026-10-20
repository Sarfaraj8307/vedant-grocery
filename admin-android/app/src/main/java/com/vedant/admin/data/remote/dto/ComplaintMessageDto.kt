package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `complaint_messages` row.
 *
 * **BLOCKED (no admin creds — R1)** — see [ComplaintDto]. Every plausible alias
 * for the body and the author is declared so a rename does not lose the text.
 *
 * For a **write**, only `complaint_id`, `author_role`, `author_id` and `body` are
 * populated; the other fields are null and, because the client serialises with
 * `explicitNulls = false`, are omitted from the INSERT rather than sent as NULLs.
 */
@Serializable
data class ComplaintMessageDto(
    val id: String? = null,
    @SerialName("complaint_id") val complaintId: String? = null,
    @SerialName("author_role") val authorRole: String? = null,
    val role: String? = null,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val body: String? = null,
    val message: String? = null,
    val content: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
