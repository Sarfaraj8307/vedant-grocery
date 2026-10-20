package com.vedant.admin.data.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.dto.ComplaintDto
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.repository.ComplaintRepository
import com.vedant.admin.testing.FakeAuthRepository
import com.vedant.admin.testing.FakeComplaintRemoteSource
import com.vedant.admin.testing.FakeStorageDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T22 acceptance for the complaint repository, proven against fakes — live
 * support access is **[BLOCKED]** (no admin creds, R1).
 *
 * The load-bearing rule is the storage path: the **first segment must be the
 * uploader's `auth.uid()`**, because the `complaint-images` RLS policy reads it.
 * The repository derives it from `AuthRepository`, refuses when there is no
 * session, and refuses an invalid payload before a byte leaves the device.
 */
class ComplaintRepositoryImplTest {

    private val remote = FakeComplaintRemoteSource()
    private val storage = FakeStorageDataSource()
    private val auth = FakeAuthRepository()
    private val repository = ComplaintRepositoryImpl(remote = remote, storage = storage, auth = auth)

    // --- attachments: the <auth.uid()>/<filename> rule ----------------------

    @Test
    fun `an attachment path is prefixed with the current user id`() = runTest {
        auth.userId = "user-42"

        val result = repository.uploadAttachment("c1", "photo.jpg", byteArrayOf(1, 2, 3), "image/jpeg")

        assertTrue(result is Outcome.Success)
        val path = storage.lastComplaintPath
        assertEquals("user-42", path?.substringBefore('/'))
        assertTrue("the RLS policy reads the first segment", path!!.startsWith("user-42/"))
    }

    @Test
    fun `an attachment is refused without a session, before any upload`() = runTest {
        auth.userId = null

        val result = repository.uploadAttachment("c1", "photo.jpg", byteArrayOf(1), "image/jpeg")

        assertTrue(result is Outcome.Failure)
        assertEquals("no byte may leave the device", 0, storage.uploadCalls)
    }

    @Test
    fun `an oversized attachment is refused before upload`() = runTest {
        val tooBig = ByteArray(ComplaintRepository.MAX_ATTACHMENT_BYTES + 1)

        val result = repository.uploadAttachment("c1", "big.jpg", tooBig, "image/jpeg")

        assertTrue(result is Outcome.Failure)
        assertEquals(0, storage.uploadCalls)
    }

    @Test
    fun `a non-image attachment is refused before upload`() = runTest {
        val result = repository.uploadAttachment("c1", "doc.pdf", byteArrayOf(1), "application/pdf")

        assertTrue(result is Outcome.Failure)
        assertEquals(0, storage.uploadCalls)
    }

    @Test
    fun `the stored attachment row records the server's path`() = runTest {
        storage.complaintPath = "user-42/stored-name.jpg"

        repository.uploadAttachment("c1", "photo.jpg", byteArrayOf(1), "image/jpeg")

        assertEquals("user-42/stored-name.jpg", remote.insertedAttachments.first().storagePath)
    }

    // --- reply --------------------------------------------------------------

    @Test
    fun `a reply is written as the store with the current user id`() = runTest {
        repository.reply("c1", "  We are sorry about that.  ")

        val written = remote.insertedMessages.first()
        assertEquals("ADMIN", written.authorRole)
        assertEquals("We are sorry about that.", written.body)
        assertEquals("u1", written.authorId)
    }

    @Test
    fun `a blank reply is refused without a request`() = runTest {
        val result = repository.reply("c1", "   ")

        assertTrue(result is Outcome.Failure)
        assertTrue(remote.insertedMessages.isEmpty())
    }

    // --- status -------------------------------------------------------------

    @Test
    fun `a status change returns the server's echoed row`() = runTest {
        remote.updateResult = ComplaintDto(id = "c1", status = "RESOLVED")

        val result = repository.changeStatus("c1", ComplaintStatus.RESOLVED)

        assertTrue(result is Outcome.Success)
        assertEquals(ComplaintStatus.RESOLVED, (result as Outcome.Success).value.status)
        assertEquals("RESOLVED", remote.updates.first().second.status)
    }

    @Test
    fun `a status change that matched no row is a failure`() = runTest {
        remote.updateResult = null

        val result = repository.changeStatus("c1", ComplaintStatus.CLOSED)

        assertTrue(result is Outcome.Failure)
    }

    // --- reads --------------------------------------------------------------

    @Test
    fun `the status facet is sent to the server while the search runs over the page`() = runTest {
        remote.listResult = listOf(
            ComplaintDto(id = "c1", subject = "Damaged tomatoes"),
            ComplaintDto(id = "c2", subject = "Late delivery"),
        )

        val result = repository.listComplaints(
            ComplaintFilter(status = ComplaintStatus.OPEN, search = "tomato"),
            page = 0,
        )

        assertEquals("OPEN", remote.lastStatusFilter)
        assertEquals(1, (result as Outcome.Success).value.size)
    }

    @Test
    fun `a complaint that no longer exists is a failure`() = runTest {
        remote.complaint = null

        val result = repository.getComplaint("missing")

        assertTrue(result is Outcome.Failure)
    }

    // --- pure helpers -------------------------------------------------------

    @Test
    fun `the attachment path helper puts the user id first`() {
        val path = ComplaintRepositoryImpl.attachmentPath("u1", "c1", "photo.jpg", 123L)
        assertEquals("u1", path.substringBefore('/'))
    }

    @Test
    fun `a filename with path separators is sanitised`() {
        val safe = ComplaintRepositoryImpl.sanitizeFileName("../../etc/passwd", "image/jpeg")

        assertFalse(safe.contains('/'))
        assertFalse(safe.contains('\\'))
        assertTrue(safe.isNotBlank())
    }

    @Test
    fun `a filename with no usable characters falls back to the content type`() {
        val safe = ComplaintRepositoryImpl.sanitizeFileName("///", "image/png")
        assertEquals("attachment.png", safe)
    }
}
