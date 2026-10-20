package com.vedant.admin.data.remote.mapper

import com.vedant.admin.data.remote.dto.ComplaintAttachmentDto
import com.vedant.admin.data.remote.dto.ComplaintDto
import com.vedant.admin.data.remote.dto.ComplaintMessageDto
import com.vedant.admin.domain.model.AuthorRole
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.ComplaintType
import com.vedant.admin.domain.model.ThreadEntry
import com.vedant.admin.domain.model.buildThread
import com.vedant.admin.testing.testComplaint
import com.vedant.admin.testing.testMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T22 acceptance for the support mapper.
 *
 * The load-bearing assertions are the enum tolerances: **`IN_PROGRESS` is the
 * live value**, and the retired `IN_REVIEW` must decode to `null` while its raw
 * string is preserved — never re-emitted, never a crash, never a dropped row.
 */
class SupportMapperTest {

    @Test
    fun `a live IN_PROGRESS status maps to the enum`() {
        val complaint = ComplaintDto(id = "c1", status = "IN_PROGRESS").toDomain()

        assertEquals(ComplaintStatus.IN_PROGRESS, complaint.status)
        assertEquals("IN_PROGRESS", complaint.statusRaw)
    }

    @Test
    fun `the retired IN_REVIEW value decodes tolerantly and is never re-emitted`() {
        val complaint = ComplaintDto(id = "c1", status = "IN_REVIEW").toDomain()

        assertNull("a retired value must not map to a live status", complaint.status)
        assertEquals("IN_REVIEW", complaint.statusRaw)
        assertEquals("IN_REVIEW", complaint.statusLabel)
    }

    @Test
    fun `an unknown status is shown raw rather than dropping the row`() {
        val complaint = ComplaintDto(id = "c1", status = "ESCALATED").toDomain()

        assertNull(complaint.status)
        assertEquals("ESCALATED", complaint.statusLabel)
    }

    @Test
    fun `all four live statuses map`() {
        listOf("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").forEach { wire ->
            val status = ComplaintStatus.fromWire(wire)
            assertEquals(wire, status?.name)
        }
    }

    @Test
    fun `complaint_type is parsed tolerantly too`() {
        assertEquals(ComplaintType.DAMAGED, ComplaintDto(id = "c1", type = "DAMAGED").toDomain().type)
        assertNull(ComplaintDto(id = "c1", type = "SOMETHING_NEW").toDomain().type)
        assertEquals("SOMETHING_NEW", ComplaintDto(id = "c1", type = "SOMETHING_NEW").toDomain().typeLabel)
    }

    @Test
    fun `the status write payload only touches status`() {
        val update = complaintStatusFields(ComplaintStatus.IN_PROGRESS)
        assertEquals("IN_PROGRESS", update.status)
    }

    @Test
    fun `a reply is always written as the store`() {
        val fields = complaintReplyFields(complaintId = "c1", body = "We are sorry.", authorId = "u1")

        assertEquals(AuthorRole.ADMIN.name, fields.authorRole)
        assertEquals("c1", fields.complaintId)
        assertEquals("We are sorry.", fields.body)
        assertEquals("u1", fields.authorId)
    }

    @Test
    fun `a message from the customer is not from the store`() {
        val message = ComplaintMessageDto(id = "m1", authorRole = "CUSTOMER", body = "hi").toDomain()

        assertEquals(AuthorRole.CUSTOMER, message.authorRole)
        assertTrue(!message.isFromStore)
    }

    @Test
    fun `an attachment keeps its stored key`() {
        val attachment = ComplaintAttachmentDto(
            id = "a1",
            storagePath = "u1/c1_1_photo.jpg",
            fileName = "photo.jpg",
        ).toDomain()

        assertEquals("u1/c1_1_photo.jpg", attachment.storagePath)
        assertEquals("photo.jpg", attachment.displayName)
        assertTrue(attachment.isImage)
    }

    @Test
    fun `the thread orders messages by time and appends the status event`() {
        val complaint = testComplaint(
            status = ComplaintStatus.RESOLVED,
            updatedAt = kotlinx.datetime.Instant.parse("2026-10-11T12:00:00Z"),
        )
        val early = testMessage(id = "m1", createdAt = kotlinx.datetime.Instant.parse("2026-10-11T09:00:00Z"))
        val late = testMessage(id = "m2", createdAt = kotlinx.datetime.Instant.parse("2026-10-11T11:00:00Z"))

        // Passed out of order on purpose: the thread must sort by instant.
        val thread = buildThread(complaint, listOf(late, early))

        assertEquals(3, thread.size)
        assertEquals(listOf("msg-m1", "msg-m2"), thread.dropLast(1).map { it.key })
        assertTrue(thread.last() is ThreadEntry.StatusEvent)
        assertEquals("Resolved", (thread.last() as ThreadEntry.StatusEvent).statusLabel)
    }

    @Test
    fun `a status event sorts into its chronological place`() {
        // The event's instant is the complaint's `updated_at`, so it can fall
        // between two messages — the thread must not blindly append it.
        val complaint = testComplaint(
            status = ComplaintStatus.IN_PROGRESS,
            updatedAt = kotlinx.datetime.Instant.parse("2026-10-11T10:00:00Z"),
        )
        val early = testMessage(id = "m1", createdAt = kotlinx.datetime.Instant.parse("2026-10-11T09:00:00Z"))
        val late = testMessage(id = "m2", createdAt = kotlinx.datetime.Instant.parse("2026-10-11T11:00:00Z"))

        val thread = buildThread(complaint, listOf(early, late))

        assertEquals(
            listOf("msg-m1", "status-In progress", "msg-m2"),
            thread.map { it.key },
        )
    }
}
