package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `complaint_attachments` row.
 *
 * **BLOCKED (no admin creds — R1)** — see [ComplaintDto]. The stored object key
 * is the field that matters (`storage_path`), with `path` declared as a plausible
 * alias; `url`/`public_url` are carried when the server provides a link, but the
 * bucket is **private**, so a bare public URL is not expected — the admin app
 * mints a short-lived signed URL instead.
 *
 * `id` is nullable because a decode of a write response is not guaranteed to be
 * shaped exactly as expected while the schema is unverified.
 */
@Serializable
data class ComplaintAttachmentDto(
    val id: String? = null,
    @SerialName("complaint_id") val complaintId: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val path: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    val name: String? = null,
    val url: String? = null,
    @SerialName("public_url") val publicUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
