package com.vedant.admin.feature.support.thread

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.ThreadEntry
import com.vedant.admin.domain.model.buildThread
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase

/**
 * Immutable state for the complaint thread screen.
 *
 * [complaint] and [messages] are the **server's** rows, re-fetched after every
 * mutation — never a locally invented post-write state. A store reply is only
 * appended once the server echoes the stored message, so the composer's pending
 * state is honest: while [replyState] is [ActionState.InFlight] nothing is shown
 * as sent.
 */
data class ComplaintThreadUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val complaint: Complaint? = null,
    val messages: List<ComplaintMessage> = emptyList(),
    val attachments: List<ComplaintAttachment> = emptyList(),
    val loadError: AppError? = null,

    /** The reply being typed. */
    val replyDraft: String = "",
    val replyState: ActionState = ActionState.Idle,
    /** A local validation message for the composer (e.g. an empty reply). */
    val composerError: String? = null,

    val statusAction: ActionState = ActionState.Idle,
    /** The status currently being applied, so the menu shows it as in-flight. */
    val pendingStatus: ComplaintStatus? = null,

    /** The file currently uploading, if any. */
    val attachmentName: String? = null,
    val attachmentProgress: Float = 0f,
    val attachmentError: AppError? = null,
) {

    /** The rendered thread: messages + the current status event, ordered by time. */
    val thread: List<ThreadEntry> get() = complaint?.let { buildThread(it, messages) } ?: emptyList()

    /** The statuses to *offer*; the server remains the authority. */
    val nextStatuses: List<ComplaintStatus> get() = complaint?.nextStatuses ?: emptyList()

    val isReplyInFlight: Boolean get() = replyState is ActionState.InFlight

    val isStatusInFlight: Boolean get() = statusAction is ActionState.InFlight

    val isUploading: Boolean get() = attachmentName != null

    /** True while any write is in flight — the composer disables itself. */
    val isSending: Boolean get() = isReplyInFlight || isStatusInFlight || isUploading

    /** A failure that should be surfaced as a banner. */
    val actionError: AppError? get() = (statusAction as? ActionState.Failed)?.error ?: attachmentError

    /** Replies are only offered while the complaint is open (not `CLOSED`). */
    val canReply: Boolean get() = complaint?.isTerminal == false
}
