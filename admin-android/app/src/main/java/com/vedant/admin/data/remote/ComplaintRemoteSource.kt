package com.vedant.admin.data.remote

import com.vedant.admin.data.remote.dto.ComplaintAttachmentDto
import com.vedant.admin.data.remote.dto.ComplaintDto
import com.vedant.admin.data.remote.dto.ComplaintMessageDto
import com.vedant.admin.data.remote.dto.ComplaintStatusUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The **PostgREST seam** for support. An interface so `ComplaintRepositoryImpl`
 * is unit-testable with a fake — live support access is **[BLOCKED]** (no admin
 * creds), so every read and write below is proven against a fake, never against
 * the real project.
 *
 * Insert/update use `Returning.Representation` (via `select()`), so PostgREST
 * echoes the **stored** row — including server defaults and generated ids. That
 * echoed row is the only thing the repository maps; the client never synthesises
 * a post-write state.
 *
 * A method returns `null` when an update/select matched **no** row; the
 * repository turns that into a `NoSuchElementException` → `AppError.Server`.
 */
interface ComplaintRemoteSource {

    /**
     * One page of complaints, newest first. [status] is the **raw** wire value
     * (`null` = no status filter); the text search is applied above this layer.
     */
    suspend fun listComplaints(status: String?, from: Long, to: Long): List<ComplaintDto>

    suspend fun getComplaint(complaintId: String): ComplaintDto?

    suspend fun updateStatus(complaintId: String, update: ComplaintStatusUpdate): ComplaintDto?

    suspend fun listMessages(complaintId: String): List<ComplaintMessageDto>

    suspend fun insertMessage(fields: ComplaintMessageDto): ComplaintMessageDto

    suspend fun listAttachments(complaintId: String): List<ComplaintAttachmentDto>

    suspend fun insertAttachment(fields: ComplaintAttachmentDto): ComplaintAttachmentDto
}

/**
 * The real [ComplaintRemoteSource], backed by PostgREST.
 *
 * `complaints` / `complaint_messages` / `complaint_attachments` are RLS-hidden
 * and their columns are **BLOCKED** (R1), so every select asks for `*` and the
 * client decodes with `ignoreUnknownKeys` (see `SupabaseProvider`): an extra live
 * column is ignored rather than fatal.
 *
 * Only `status` (an evidenced enum, `docs/CONTRACT.md` §4) is used as a
 * server-side filter. The free-text search is applied in the repository — see the
 * note there.
 */
class SupabaseComplaintRemoteSource(private val client: SupabaseClient) : ComplaintRemoteSource {

    override suspend fun listComplaints(status: String?, from: Long, to: Long): List<ComplaintDto> =
        client.postgrest.from(TABLE_COMPLAINTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                status?.let { wire -> filter { eq("status", wire) } }
                order("created_at", Order.DESCENDING)
                range(from, to)
            }
            .decodeList()

    override suspend fun getComplaint(complaintId: String): ComplaintDto? =
        client.postgrest.from(TABLE_COMPLAINTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("id", complaintId) }
                limit(1)
            }
            .decodeList<ComplaintDto>()
            .firstOrNull()

    override suspend fun updateStatus(complaintId: String, update: ComplaintStatusUpdate): ComplaintDto? =
        client.postgrest.from(TABLE_COMPLAINTS)
            .update(update) {
                filter { eq("id", complaintId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun listMessages(complaintId: String): List<ComplaintMessageDto> =
        client.postgrest.from(TABLE_MESSAGES)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("complaint_id", complaintId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun insertMessage(fields: ComplaintMessageDto): ComplaintMessageDto =
        client.postgrest.from(TABLE_MESSAGES)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun listAttachments(complaintId: String): List<ComplaintAttachmentDto> =
        client.postgrest.from(TABLE_ATTACHMENTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter { eq("complaint_id", complaintId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun insertAttachment(fields: ComplaintAttachmentDto): ComplaintAttachmentDto =
        client.postgrest.from(TABLE_ATTACHMENTS)
            .insert(fields) { select() }
            .decodeSingle()

    private companion object {
        const val ALL_COLUMNS = "*"

        const val TABLE_COMPLAINTS = "complaints"
        const val TABLE_MESSAGES = "complaint_messages"
        const val TABLE_ATTACHMENTS = "complaint_attachments"
    }
}
