package com.vedant.admin.domain.model

import kotlinx.datetime.Instant

/**
 * The lifecycle of a customer complaint.
 *
 * ### Wire values (live — `docs/CONTRACT.md` §4)
 * `OPEN · IN_PROGRESS · RESOLVED · CLOSED`. **`IN_PROGRESS`, never `IN_REVIEW`** —
 * `IN_REVIEW` is a retired value that must not be emitted. The enum is parsed
 * **tolerantly**: a value this build does not know decodes to `null` and the raw
 * string is carried alongside it, so a server-side addition renders as its raw
 * wire value instead of crashing or dropping the row (§5.2).
 */
enum class ComplaintStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    ;

    /** Short, human wording for a badge or a filter chip. */
    val label: String
        get() = when (this) {
            OPEN -> "Open"
            IN_PROGRESS -> "In progress"
            RESOLVED -> "Resolved"
            CLOSED -> "Closed"
        }

    /** `CLOSED` is the only terminal state — no further replies are expected. */
    val isTerminal: Boolean get() = this == CLOSED

    companion object {
        /** Tolerant wire parse: unknown values yield `null`, never a throw. */
        fun fromWire(raw: String?): ComplaintStatus? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * What the complaint is about.
 *
 * Wire values (live): `WRONG_ITEM · DAMAGED · MISSING_ITEM · LATE_DELIVERY ·
 * QUALITY · OTHER`. Parsed tolerantly like [ComplaintStatus].
 */
enum class ComplaintType {
    WRONG_ITEM,
    DAMAGED,
    MISSING_ITEM,
    LATE_DELIVERY,
    QUALITY,
    OTHER,
    ;

    val label: String
        get() = when (this) {
            WRONG_ITEM -> "Wrong item"
            DAMAGED -> "Damaged"
            MISSING_ITEM -> "Missing item"
            LATE_DELIVERY -> "Late delivery"
            QUALITY -> "Quality"
            OTHER -> "Other"
        }

    companion object {
        fun fromWire(raw: String?): ComplaintType? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * Who wrote a message. The live `author_role` enum is `CUSTOMER | ADMIN`;
 * parsed tolerantly so a future third role does not break the thread.
 */
enum class AuthorRole {
    CUSTOMER,
    ADMIN,
    ;

    val label: String
        get() = when (this) {
            CUSTOMER -> "Customer"
            ADMIN -> "Store"
        }

    companion object {
        fun fromWire(raw: String?): AuthorRole? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * One customer complaint.
 *
 * ### Column availability: BLOCKED (no admin creds — R1)
 * `complaints` is RLS-hidden, so its live columns could not be enumerated
 * (`docs/CONTRACT.md` §10). Every field except [id] is therefore nullable or has
 * a tolerant fallback, and the raw wire strings for the two enums are kept so an
 * unknown value is still shown verbatim rather than guessed.
 */
data class Complaint(
    val id: String,
    val orderId: String?,
    val customerRef: String?,
    val customerName: String?,
    val subject: String?,
    val status: ComplaintStatus?,
    val statusRaw: String,
    val type: ComplaintType?,
    val typeRaw: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
) {

    /** e.g. `Open`, or the raw wire value when the build does not know it. */
    val statusLabel: String get() = status?.label ?: statusRaw.ifBlank { "Unknown" }

    /** e.g. `Damaged`, or the raw wire value. */
    val typeLabel: String get() = type?.label ?: typeRaw?.takeIf { it.isNotBlank() } ?: "—"

    /** A short handle for a dense list row. */
    val shortId: String get() = id.take(8).uppercase()

    /** The heading a row shows; falls back to the handle when there is no subject. */
    val title: String get() = subject?.takeIf { it.isNotBlank() } ?: "Complaint $shortId"

    /** True once the complaint can no longer be actioned. */
    val isTerminal: Boolean get() = status?.isTerminal == true

    /** The status this complaint can move to next, or `null` when terminal. */
    val nextStatuses: List<ComplaintStatus>
        get() = if (isTerminal) emptyList() else ComplaintStatus.entries.filterNot { it == status }

    /**
     * A case-insensitive match over the fields a person would search by.
     *
     * The complaint columns are **BLOCKED** (RLS-hidden, R1), so a server-side
     * `ilike` would have to name a column we cannot verify — and a wrong column
     * name makes PostgREST reject the whole request. Searching the loaded page
     * keeps the list working; the trade-off is documented in
     * `ComplaintRepositoryImpl`.
     */
    fun matches(term: String): Boolean {
        val needle = term.trim().lowercase()
        if (needle.isEmpty()) return true
        return listOfNotNull(subject, customerName, orderId, shortId, statusLabel, typeLabel)
            .any { it.lowercase().contains(needle) }
    }
}

/** One message in a complaint thread. */
data class ComplaintMessage(
    val id: String,
    val complaintId: String?,
    val authorRole: AuthorRole?,
    val authorRoleRaw: String?,
    val authorId: String?,
    val body: String,
    val createdAt: Instant,
) {

    /** True when the store wrote it (the only role the admin app ever writes). */
    val isFromStore: Boolean get() = authorRole == AuthorRole.ADMIN

    /** e.g. `Customer`, `Store`, or the raw wire value. */
    val authorLabel: String
        get() = authorRole?.label ?: authorRoleRaw?.takeIf { it.isNotBlank() } ?: "Unknown"
}

/** One file attached to a complaint. */
data class ComplaintAttachment(
    val id: String,
    val complaintId: String?,
    val storagePath: String,
    val fileName: String?,
    val url: String?,
    val createdAt: Instant?,
) {

    /** A human label for the file. */
    val displayName: String
        get() = fileName?.takeIf { it.isNotBlank() } ?: storagePath.substringAfterLast('/')

    private val extension: String get() = displayName.substringAfterLast('.', "").lowercase()

    /** True when the file is one of the image types the bucket accepts. */
    val isImage: Boolean get() = extension in IMAGE_EXTENSIONS

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}

/**
 * The complaint-list query: an optional status facet and a free-text search.
 * Applied **server-side** so a filter never hides a match that simply was not on
 * the loaded page.
 */
data class ComplaintFilter(
    val status: ComplaintStatus? = null,
    val search: String = "",
) {

    val searchTerm: String? get() = search.trim().takeIf { it.isNotEmpty() }

    val isActive: Boolean get() = status != null || searchTerm != null

    companion object {
        val NONE = ComplaintFilter()
    }
}

/**
 * One entry in a rendered complaint thread.
 *
 * The thread mixes **customer messages**, **store replies** and **status events**
 * so the UI can render them distinctly (a messaging app, not a flat list). A
 * status event is derived from the complaint row itself (its current status and
 * its last-updated instant) — nothing is invented.
 */
sealed interface ThreadEntry {

    /** A stable key for the list. */
    val key: String

    /** When the entry happened. */
    val at: Instant

    /** A customer or store message. */
    data class Message(val message: ComplaintMessage) : ThreadEntry {
        override val key: String get() = "msg-${message.id}"
        override val at: Instant get() = message.createdAt
    }

    /** The complaint's status as of its last update. */
    data class StatusEvent(
        val statusLabel: String,
        val status: ComplaintStatus?,
        val note: String?,
        override val at: Instant,
    ) : ThreadEntry {
        override val key: String get() = "status-$statusLabel"
    }
}

/**
 * Builds the rendered thread: the messages, plus the complaint's current status
 * as a distinct event, ordered by time.
 *
 * Pure and total — an empty message list still yields the status event, so the
 * screen always has something meaningful to show. The event's instant is the
 * complaint's `updated_at` (falling back to `created_at`) — derived from the row,
 * never invented.
 */
fun buildThread(complaint: Complaint, messages: List<ComplaintMessage>): List<ThreadEntry> {
    val statusAt = complaint.updatedAt ?: complaint.createdAt
    val statusEntry: ThreadEntry = ThreadEntry.StatusEvent(
        statusLabel = complaint.statusLabel,
        status = complaint.status,
        note = null,
        at = statusAt,
    )
    val entries: List<ThreadEntry> = messages.map { ThreadEntry.Message(it) } + statusEntry
    return entries.sortedBy { it.at }
}
