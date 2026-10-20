package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.data.remote.ComplaintRemoteSource
import com.vedant.admin.data.remote.StorageDataSource
import com.vedant.admin.data.remote.mapper.complaintAttachmentFields
import com.vedant.admin.data.remote.mapper.complaintReplyFields
import com.vedant.admin.data.remote.mapper.complaintStatusFields
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.ComplaintRepository

private const val TAG = "ComplaintRepository"

/**
 * The real [ComplaintRepository].
 *
 * It composes three replaceable collaborators:
 *  - [ComplaintRemoteSource] — the PostgREST **reads/writes**.
 *  - [StorageDataSource]     — the **upload** seam for the private
 *    `complaint-images` bucket.
 *  - [AuthRepository]        — the only correct source of the `auth.uid()` that
 *    must be the **first path segment** of an attachment key.
 *
 * ### Never fake success
 * A write returns the row **PostgREST echoed**, never the request. If the request
 * throws (network, `42501`, an RLS denial on the storage path, a constraint) the
 * exception propagates as `Outcome.Failure` and nothing is mapped — so the
 * ViewModel has no new state and the UI does not move.
 *
 * ### Why the text search is client-side
 * `complaints` is RLS-hidden and its columns are **BLOCKED** (R1). A server-side
 * `ilike` would have to name a column we cannot verify, and a wrong name makes
 * PostgREST reject the **whole** request — blanking the list. The status facet
 * (an evidenced enum) is server-side; the free-text match runs over the loaded
 * page. That is a deliberate, documented trade-off, not an oversight.
 */
class ComplaintRepositoryImpl(
    private val remote: ComplaintRemoteSource,
    private val storage: StorageDataSource,
    private val auth: AuthRepository,
) : ComplaintRepository {

    override suspend fun listComplaints(
        filter: ComplaintFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Complaint>> = outcomeOf {
        val from = page.coerceAtLeast(0).toLong() * pageSize
        val loaded = remote.listComplaints(filter.status?.name, from, from + pageSize - 1)
            .map { it.toDomain() }
        val term = filter.searchTerm
        if (term == null) loaded else loaded.filter { it.matches(term) }
    }

    override suspend fun getComplaint(complaintId: String): Outcome<Complaint> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        remote.getComplaint(complaintId)?.toDomain() ?: throw notFound(complaintId)
    }

    override suspend fun listMessages(complaintId: String): Outcome<List<ComplaintMessage>> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        remote.listMessages(complaintId).map { it.toDomain() }
    }

    override suspend fun changeStatus(
        complaintId: String,
        status: ComplaintStatus,
    ): Outcome<Complaint> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        AppLog.i(TAG, "changeStatus $complaintId -> ${status.name}")
        remote.updateStatus(complaintId, complaintStatusFields(status))?.toDomain()
            ?: throw notFound(complaintId)
    }

    override suspend fun reply(complaintId: String, body: String): Outcome<ComplaintMessage> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        val clean = body.trim()
        require(clean.isNotEmpty()) { "Write a reply before sending." }
        val authorId = auth.currentUserId()
        AppLog.i(TAG, "reply to $complaintId as ADMIN")
        remote.insertMessage(complaintReplyFields(complaintId, clean, authorId)).toDomain()
    }

    override suspend fun listAttachments(complaintId: String): Outcome<List<ComplaintAttachment>> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        remote.listAttachments(complaintId).map { it.toDomain() }
    }

    override suspend fun uploadAttachment(
        complaintId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<ComplaintAttachment> = outcomeOf {
        require(complaintId.isNotBlank()) { "A complaint id is required." }
        validateAttachment(bytes, mimeType)

        // The storage RLS policy authorises on the FIRST path segment, so this is
        // the only correct source for it.
        val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in again before attaching a file.")

        val safeName = sanitizeFileName(fileName, mimeType)
        val path = attachmentPath(userId, complaintId, safeName, AppClock.now().toEpochMilliseconds())
        AppLog.i(TAG, "uploading attachment for $complaintId")

        val storedPath = storage.uploadComplaintAttachment(path, bytes, mimeType, onProgress)
        remote.insertAttachment(complaintAttachmentFields(complaintId, storedPath, safeName)).toDomain()
    }

    /** Rejects a payload the bucket would reject anyway, before it leaves the device. */
    private fun validateAttachment(bytes: ByteArray, mimeType: String) {
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        require(bytes.size <= ComplaintRepository.MAX_ATTACHMENT_BYTES) {
            "Files must be 5 MB or smaller."
        }
        require(mimeType.lowercase() in ComplaintRepository.ALLOWED_ATTACHMENT_MIME) {
            "Files must be JPEG, PNG or WebP."
        }
    }

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Complaint $id no longer exists.")

    companion object {
        /**
         * Builds the object key for an attachment.
         *
         * The **first segment is the uploader's `auth.uid()`** — the storage RLS
         * policy reads it to authorise the owner. The rest is a collision-safe
         * file name so two attachments with the same original name do not
         * overwrite each other. Pure, so the rule is directly assertable.
         */
        fun attachmentPath(
            userId: String,
            complaintId: String,
            safeFileName: String,
            stampMillis: Long,
        ): String = "$userId/${complaintId}_${stampMillis}_$safeFileName"

        /** Strips separators and anything exotic; keeps a readable name. */
        fun sanitizeFileName(fileName: String, mimeType: String): String {
            val base = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
            val cleaned = base.map { ch ->
                if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_'
            }.joinToString("")
            return cleaned.trim('.', '_').takeIf { it.isNotBlank() }
                ?: "attachment.${extensionFor(mimeType)}"
        }

        private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "bin"
        }
    }
}
