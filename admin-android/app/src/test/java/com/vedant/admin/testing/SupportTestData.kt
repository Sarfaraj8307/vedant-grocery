package com.vedant.admin.testing

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.ComplaintRemoteSource
import com.vedant.admin.data.remote.StorageDataSource
import com.vedant.admin.data.remote.dto.ComplaintAttachmentDto
import com.vedant.admin.data.remote.dto.ComplaintDto
import com.vedant.admin.data.remote.dto.ComplaintMessageDto
import com.vedant.admin.data.remote.dto.ComplaintStatusUpdate
import com.vedant.admin.domain.model.AuthorRole
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.ComplaintType
import com.vedant.admin.domain.repository.ComplaintRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Instant

/**
 * Test data and fakes for the complaints feature.
 *
 * Live support access is **[BLOCKED]** (no admin creds, R1), so every repository
 * rule — the `<auth.uid()>/<filename>` storage path, the `author_role = ADMIN`
 * reply, the "server-confirmed only" contract — is proven against these fakes.
 */

private val FIXED_INSTANT = Instant.parse("2026-10-11T10:00:00Z")

fun testComplaint(
    id: String = "c1",
    orderId: String? = "o1",
    customerName: String? = "Asha",
    subject: String? = "Damaged tomatoes",
    status: ComplaintStatus? = ComplaintStatus.OPEN,
    statusRaw: String = status?.name ?: "OPEN",
    type: ComplaintType? = ComplaintType.DAMAGED,
    createdAt: Instant = FIXED_INSTANT,
    updatedAt: Instant? = null,
): Complaint = Complaint(
    id = id,
    orderId = orderId,
    customerRef = "cust-1",
    customerName = customerName,
    subject = subject,
    status = status,
    statusRaw = statusRaw,
    type = type,
    typeRaw = type?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testMessage(
    id: String = "m1",
    authorRole: AuthorRole? = AuthorRole.CUSTOMER,
    body: String = "The tomatoes were damaged.",
    createdAt: Instant = FIXED_INSTANT,
): ComplaintMessage = ComplaintMessage(
    id = id,
    complaintId = "c1",
    authorRole = authorRole,
    authorRoleRaw = authorRole?.name,
    authorId = "u1",
    body = body,
    createdAt = createdAt,
)

fun testAttachment(
    id: String = "a1",
    storagePath: String = "u1/c1_1_photo.jpg",
    fileName: String? = "photo.jpg",
): ComplaintAttachment = ComplaintAttachment(
    id = id,
    complaintId = "c1",
    storagePath = storagePath,
    fileName = fileName,
    url = null,
    createdAt = FIXED_INSTANT,
)

/** A scriptable [ComplaintRepository] for the ViewModel tests. */
class FakeComplaintRepository : ComplaintRepository {

    var listResult: Outcome<List<Complaint>> = Outcome.Success(emptyList())
    var getResult: Outcome<Complaint> = Outcome.Success(testComplaint())
    var messagesResult: Outcome<List<ComplaintMessage>> = Outcome.Success(emptyList())
    var attachmentsResult: Outcome<List<ComplaintAttachment>> = Outcome.Success(emptyList())
    var statusResult: Outcome<Complaint> = Outcome.Success(testComplaint())
    var replyResult: Outcome<ComplaintMessage> = Outcome.Success(testMessage(authorRole = AuthorRole.ADMIN))
    var uploadResult: Outcome<ComplaintAttachment> = Outcome.Success(testAttachment())

    val listRequests = mutableListOf<Pair<ComplaintFilter, Int>>()
    var getCalls = 0
    var replyCalls = 0
    var statusCalls = 0
    var uploadCalls = 0
    var lastReplyBody: String? = null
    var lastStatus: ComplaintStatus? = null
    var lastUpload: Triple<String, String, ByteArray>? = null

    /** When non-null, a write suspends on it — lets a test observe the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun listComplaints(
        filter: ComplaintFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<Complaint>> {
        listRequests += filter to page
        return listResult
    }

    override suspend fun getComplaint(complaintId: String): Outcome<Complaint> {
        getCalls++
        return getResult
    }

    override suspend fun listMessages(complaintId: String): Outcome<List<ComplaintMessage>> = messagesResult

    override suspend fun changeStatus(complaintId: String, status: ComplaintStatus): Outcome<Complaint> {
        statusCalls++
        lastStatus = status
        gate?.await()
        return statusResult
    }

    override suspend fun reply(complaintId: String, body: String): Outcome<ComplaintMessage> {
        replyCalls++
        lastReplyBody = body
        gate?.await()
        return replyResult
    }

    override suspend fun listAttachments(complaintId: String): Outcome<List<ComplaintAttachment>> = attachmentsResult

    override suspend fun uploadAttachment(
        complaintId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): Outcome<ComplaintAttachment> {
        uploadCalls++
        lastUpload = Triple(fileName, mimeType, bytes)
        onProgress(1f)
        gate?.await()
        return uploadResult
    }
}

/** A fake PostgREST seam for the complaint repository tests. */
class FakeComplaintRemoteSource : ComplaintRemoteSource {

    var listResult: List<ComplaintDto> = emptyList()
    var complaint: ComplaintDto? = ComplaintDto(id = "c1")
    var updateResult: ComplaintDto? = ComplaintDto(id = "c1", status = "IN_PROGRESS")
    var messages: List<ComplaintMessageDto> = emptyList()
    var insertMessageResult: ComplaintMessageDto = ComplaintMessageDto(id = "m1", authorRole = "ADMIN", body = "hi")
    var attachments: List<ComplaintAttachmentDto> = emptyList()
    var insertAttachmentResult: ComplaintAttachmentDto = ComplaintAttachmentDto(id = "a1", storagePath = "u1/x.jpg")

    val updates = mutableListOf<Pair<String, ComplaintStatusUpdate>>()
    val insertedMessages = mutableListOf<ComplaintMessageDto>()
    val insertedAttachments = mutableListOf<ComplaintAttachmentDto>()
    var lastStatusFilter: String? = null
    var lastRange: Pair<Long, Long>? = null

    override suspend fun listComplaints(status: String?, from: Long, to: Long): List<ComplaintDto> {
        lastStatusFilter = status
        lastRange = from to to
        return listResult
    }

    override suspend fun getComplaint(complaintId: String): ComplaintDto? = complaint

    override suspend fun updateStatus(complaintId: String, update: ComplaintStatusUpdate): ComplaintDto? {
        updates += complaintId to update
        return updateResult
    }

    override suspend fun listMessages(complaintId: String): List<ComplaintMessageDto> = messages

    override suspend fun insertMessage(fields: ComplaintMessageDto): ComplaintMessageDto {
        insertedMessages += fields
        return insertMessageResult
    }

    override suspend fun listAttachments(complaintId: String): List<ComplaintAttachmentDto> = attachments

    override suspend fun insertAttachment(fields: ComplaintAttachmentDto): ComplaintAttachmentDto {
        insertedAttachments += fields
        return insertAttachmentResult
    }
}

/** A fake upload seam that records the path it was asked to store. */
class FakeStorageDataSource : StorageDataSource {

    var productUrl: String = "https://example.test/product-images/x.jpg"
    var complaintPath: String = "u1/stored.jpg"
    var signedUrl: String? = "https://example.test/signed"
    var failWith: Throwable? = null

    var lastProductPath: String? = null
    var lastComplaintPath: String? = null
    var lastSignedPath: String? = null
    var uploadCalls = 0

    override suspend fun uploadProductImage(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String {
        uploadCalls++
        lastProductPath = path
        failWith?.let { throw it }
        onProgress(1f)
        return productUrl
    }

    override suspend fun uploadComplaintAttachment(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String {
        uploadCalls++
        lastComplaintPath = path
        failWith?.let { throw it }
        onProgress(1f)
        return complaintPath
    }

    override suspend fun signedComplaintUrl(path: String, expiresInSeconds: Long): String? {
        lastSignedPath = path
        return signedUrl
    }
}
