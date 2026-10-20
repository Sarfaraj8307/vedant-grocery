package com.vedant.admin.feature.support.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.getOrNull
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.repository.ComplaintRepository
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ComplaintThread"

/**
 * Drives the complaint thread and its three writes (reply, status change,
 * attachment).
 *
 * Every non-negotiable lives here, not in the composable:
 *  - **Never fake success** — a reply is only appended once the server echoes the
 *    stored message; a status change only replaces [ComplaintThreadUiState.complaint]
 *    with the server's row. On failure nothing is appended and the UI does not move.
 *  - **No double-submit** — while any write is in flight the composer and the
 *    status menu refuse another.
 *  - **The offered transitions are a convenience** — a move the server rejects is
 *    surfaced, never assumed.
 */
class ComplaintThreadViewModel(
    private val complaintRepository: ComplaintRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComplaintThreadUiState())
    val uiState: StateFlow<ComplaintThreadUiState> = _uiState.asStateFlow()

    private var complaintId: String? = null

    /** Idempotent: binds the screen to a complaint id and loads it once. */
    fun start(id: String) {
        if (complaintId == id) return
        complaintId = id
        load()
    }

    fun reload() = load()

    // --- Reply --------------------------------------------------------------

    fun onReplyChange(text: String) {
        _uiState.update { it.copy(replyDraft = text, composerError = null) }
    }

    /**
     * Sends the store's reply. The message is appended only from the server's
     * echoed row, so the composer's pending state is never a lie.
     */
    fun sendReply() {
        val state = _uiState.value
        if (state.isSending) return
        val id = complaintId ?: return
        val body = state.replyDraft.trim()
        if (body.isEmpty()) {
            _uiState.update { it.copy(composerError = "Write a reply before sending.") }
            return
        }

        _uiState.update { it.copy(replyState = ActionState.InFlight, composerError = null) }
        viewModelScope.launch {
            when (val outcome = complaintRepository.reply(id, body)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        replyState = ActionState.Idle,
                        replyDraft = "",
                        // The server's row, not the text we typed.
                        messages = appendMessage(it.messages, outcome.value),
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    // The draft is kept so nothing the operator typed is lost.
                    it.copy(replyState = ActionState.Failed(outcome.error))
                }
            }
        }
    }

    // --- Status -------------------------------------------------------------

    fun changeStatus(status: ComplaintStatus) {
        val state = _uiState.value
        if (state.isStatusInFlight || state.isSending) return
        val id = complaintId ?: return
        if (status == state.complaint?.status) return

        _uiState.update { it.copy(statusAction = ActionState.InFlight, pendingStatus = status) }
        viewModelScope.launch {
            when (val outcome = complaintRepository.changeStatus(id, status)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        complaint = outcome.value,
                        statusAction = ActionState.Idle,
                        pendingStatus = null,
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(statusAction = ActionState.Failed(outcome.error), pendingStatus = null)
                }
            }
        }
    }

    /** Clears a surfaced failure once the operator has read it. */
    fun dismissActionError() {
        _uiState.update { it.copy(statusAction = ActionState.Idle, attachmentError = null) }
    }

    // --- Attachment ---------------------------------------------------------

    /**
     * Uploads [bytes] to the private `complaint-images` bucket and records the
     * row. The repository derives the `<auth.uid()>/…` path the storage RLS policy
     * requires; on success the attachment list is re-read so only server-confirmed
     * rows are shown.
     */
    fun attachFile(fileName: String, mimeType: String, bytes: ByteArray) {
        val state = _uiState.value
        if (state.isSending) return
        val id = complaintId ?: return

        _uiState.update {
            it.copy(attachmentName = fileName, attachmentProgress = 0f, attachmentError = null)
        }
        viewModelScope.launch {
            val outcome = complaintRepository.uploadAttachment(id, fileName, bytes, mimeType) { progress ->
                _uiState.update { it.copy(attachmentProgress = progress.coerceIn(0f, 1f)) }
            }
            when (outcome) {
                is Outcome.Success -> {
                    // Re-read so the list reflects the server, not our optimism.
                    val refreshed = complaintRepository.listAttachments(id).getOrNull()
                        ?: _uiState.value.attachments
                    _uiState.update {
                        it.copy(
                            attachments = refreshed,
                            attachmentName = null,
                            attachmentProgress = 0f,
                            attachmentError = null,
                        )
                    }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(attachmentName = null, attachmentProgress = 0f, attachmentError = outcome.error)
                }
            }
        }
    }

    // --- Load ---------------------------------------------------------------

    private fun load() {
        val id = complaintId ?: return
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = complaintRepository.getComplaint(id)) {
                is Outcome.Success -> {
                    // The thread and attachments are bonus reads: a failure there
                    // must not blank the complaint itself.
                    val messages = complaintRepository.listMessages(id).getOrNull().orEmpty()
                    val attachments = complaintRepository.listAttachments(id).getOrNull().orEmpty()
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Data,
                            complaint = outcome.value,
                            messages = messages,
                            attachments = attachments,
                            loadError = null,
                        )
                    }
                }

                is Outcome.Failure -> {
                    AppLog.w(TAG, "could not load complaint $id")
                    _uiState.update { it.copy(phase = ContentPhase.Error, loadError = outcome.error) }
                }
            }
        }
    }

    private companion object {
        /** Keeps the thread ordered and de-duplicated when a message is re-read. */
        fun appendMessage(
            existing: List<ComplaintMessage>,
            message: ComplaintMessage,
        ): List<ComplaintMessage> =
            if (existing.any { it.id.isNotBlank() && it.id == message.id }) existing
            else existing + message
    }
}
