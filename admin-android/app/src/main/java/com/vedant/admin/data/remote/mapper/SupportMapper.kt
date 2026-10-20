package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.data.remote.dto.ComplaintAttachmentDto
import com.vedant.admin.data.remote.dto.ComplaintDto
import com.vedant.admin.data.remote.dto.ComplaintMessageDto
import com.vedant.admin.data.remote.dto.ComplaintStatusUpdate
import com.vedant.admin.domain.model.AuthorRole
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.ComplaintType
import kotlinx.datetime.Instant

private const val TAG = "SupportMapper"

/**
 * `complaints` / `complaint_messages` / `complaint_attachments` → domain.
 *
 * ### Tolerant by design (§5.2)
 * `complaint_status` and `complaint_type` are parsed through their enums'
 * `fromWire`, which returns `null` for a value this build does not know — the
 * **raw** string is carried alongside so the UI shows what the database holds
 * rather than crashing or dropping the row. `IN_PROGRESS` is the live value;
 * `IN_REVIEW` is retired and simply decodes to `null` (never re-emitted).
 *
 * ### Write side
 * [complaintStatusFields], [complaintReplyFields] and [complaintAttachmentFields]
 * build the wire DTOs. A null field is **omitted** from the request
 * (`explicitNulls = false`), so a write never sends a NULL over a live column it
 * did not mean to touch.
 */

// --- Read -------------------------------------------------------------------

fun ComplaintDto.toDomain(): Complaint = Complaint(
    id = id,
    orderId = orderId,
    customerRef = customerId ?: userId,
    customerName = customerName,
    subject = subject ?: description ?: message,
    status = ComplaintStatus.fromWire(status),
    statusRaw = status?.trim().orEmpty(),
    type = ComplaintType.fromWire(type ?: complaintType),
    typeRaw = (type ?: complaintType)?.trim(),
    createdAt = parseInstantOrNull(createdAt) ?: missingInstant("complaints.created_at"),
    updatedAt = parseInstantOrNull(updatedAt),
)

fun ComplaintMessageDto.toDomain(): ComplaintMessage {
    val role = authorRole ?: role
    return ComplaintMessage(
        id = id.orEmpty(),
        complaintId = complaintId,
        authorRole = AuthorRole.fromWire(role),
        authorRoleRaw = role?.trim(),
        authorId = authorId ?: userId,
        body = body ?: message ?: content ?: "",
        createdAt = parseInstantOrNull(createdAt) ?: missingInstant("complaint_messages.created_at"),
    )
}

fun ComplaintAttachmentDto.toDomain(): ComplaintAttachment {
    val path = storagePath ?: path.orEmpty()
    return ComplaintAttachment(
        id = id.orEmpty(),
        complaintId = complaintId,
        storagePath = path,
        fileName = fileName ?: name,
        url = url ?: publicUrl,
        createdAt = parseInstantOrNull(createdAt),
    )
}

// --- Write ------------------------------------------------------------------

/**
 * The status change for a complaint. Only `status` is sent, so the request cannot
 * clobber another column.
 */
fun complaintStatusFields(status: ComplaintStatus): ComplaintStatusUpdate =
    ComplaintStatusUpdate(status = status.name)

/**
 * A store reply. `author_role = ADMIN` is what marks it as the store's — the
 * live enum is `CUSTOMER | ADMIN`.
 */
fun complaintReplyFields(
    complaintId: String,
    body: String,
    authorId: String?,
): ComplaintMessageDto = ComplaintMessageDto(
    complaintId = complaintId,
    authorRole = AuthorRole.ADMIN.name,
    authorId = authorId,
    body = body,
)

/** The attachment row for an object already stored in `complaint-images`. */
fun complaintAttachmentFields(
    complaintId: String,
    storagePath: String,
    fileName: String?,
): ComplaintAttachmentDto = ComplaintAttachmentDto(
    complaintId = complaintId,
    storagePath = storagePath,
    fileName = fileName,
)

// --- Helpers ----------------------------------------------------------------

/**
 * A row with no readable timestamp is a data problem, not a reason to drop the
 * complaint — log it and fall back to the epoch so the row still renders.
 */
private fun missingInstant(field: String): Instant {
    AppLog.w(TAG, "$field was missing or unparseable; defaulting to epoch")
    return Instant.fromEpochMilliseconds(0L)
}
