package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus

/**
 * Support / complaints for the admin app.
 *
 * ### Never fake success
 * Every write returns the **server-confirmed row**: [changeStatus] returns the
 * re-read complaint, [reply] returns the row the server stored (with its real id
 * and timestamp). A rejection surfaces as `Outcome.Failure` and the UI does not
 * move.
 *
 * ### Attachments are private
 * Files go to the **private** `complaint-images` bucket, and the object path
 * **must** start with the uploader's `auth.uid()` — the storage RLS policy reads
 * the first path segment to authorise the owner (`docs/CONTRACT.md` §6). The
 * repository derives that segment from `AuthRepository.currentUserId()`; a path
 * that does not match is rejected by the server, so getting it wrong is visible
 * rather than silent.
 *
 * ### Live access is BLOCKED
 * `complaints` / `complaint_messages` / `complaint_attachments` are RLS-hidden
 * and their columns are unenumerated (R1), so this contract is proven against a
 * fake remote source and the DTOs are deliberately tolerant.
 */
interface ComplaintRepository {

    /** One page of complaints matching [filter], filtered and ordered server-side. */
    suspend fun listComplaints(
        filter: ComplaintFilter = ComplaintFilter.NONE,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Outcome<List<Complaint>>

    /** One complaint, or a `Failure` when it no longer exists. */
    suspend fun getComplaint(complaintId: String): Outcome<Complaint>

    /** The thread's messages, oldest first. */
    suspend fun listMessages(complaintId: String): Outcome<List<ComplaintMessage>>

    /** Moves a complaint to [status] and returns the server-confirmed row. */
    suspend fun changeStatus(complaintId: String, status: ComplaintStatus): Outcome<Complaint>

    /**
     * Replies as the **store**: the row is written with `author_role = 'ADMIN'`.
     * Returns the server-confirmed message.
     */
    suspend fun reply(complaintId: String, body: String): Outcome<ComplaintMessage>

    /** The files attached to one complaint. */
    suspend fun listAttachments(complaintId: String): Outcome<List<ComplaintAttachment>>

    /**
     * Uploads a file to the private `complaint-images` bucket at
     * `<currentUserId()>/<filename>` and records the row. Returns the
     * server-confirmed attachment.
     *
     * [onProgress] receives `0f..1f` while the bytes are in flight. The payload is
     * validated against the bucket's own limits ([MAX_ATTACHMENT_BYTES],
     * [ALLOWED_ATTACHMENT_MIME]) before a single byte is sent.
     */
    suspend fun uploadAttachment(
        complaintId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit = {},
    ): Outcome<ComplaintAttachment>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        /** The `complaint-images` bucket limit (5 MB). */
        const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

        /** The content types the `complaint-images` bucket accepts. */
        val ALLOWED_ATTACHMENT_MIME: Set<String> = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
        )
    }
}
