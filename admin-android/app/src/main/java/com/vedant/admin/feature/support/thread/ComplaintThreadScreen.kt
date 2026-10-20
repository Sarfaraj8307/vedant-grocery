package com.vedant.admin.feature.support.thread

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.ComplaintAttachment
import com.vedant.admin.domain.model.ComplaintMessage
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.ThreadEntry
import com.vedant.admin.feature.catalogue.editor.ImageProcessor
import com.vedant.admin.ui.components.ComplaintStatusBadge
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S8 — Complaint thread.
 *
 * Reads like a messaging app: **customer messages** sit on the left in a muted
 * bubble, **store replies** on the right in the brand-tinted bubble, and
 * **status changes** appear as a centred, dated event — three visually distinct
 * kinds so the conversation is easy to scan.
 *
 * The composer is pinned to the bottom and reflects its own pending state: while
 * a reply is in flight the field and the send button are disabled and a
 * "Sending…" strip appears, so nothing on screen claims to have been sent until
 * the server echoes it back.
 */
@Composable
fun ComplaintThreadScreen(
    factory: ViewModelProvider.Factory,
    complaintId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ComplaintThreadViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(complaintId) { viewModel.start(complaintId) }

    ComplaintThreadContent(
        state = state,
        onBack = onBack,
        onReload = viewModel::reload,
        onChangeStatus = viewModel::changeStatus,
        onReplyChange = viewModel::onReplyChange,
        onSendReply = viewModel::sendReply,
        onAttach = viewModel::attachFile,
        onDismissError = viewModel::dismissActionError,
        modifier = modifier,
    )
}

@Composable
private fun ComplaintThreadContent(
    state: ComplaintThreadUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onChangeStatus: (ComplaintStatus) -> Unit,
    onReplyChange: (String) -> Unit,
    onSendReply: () -> Unit,
    onAttach: (String, String, ByteArray) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    // A reply or a status change cannot reach the server while offline, so the
    // controls are disabled rather than queued (T24).
    val online = LocalOnlineStatus.current

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = state.complaint?.title ?: "Complaint",
            subtitle = state.complaint?.statusLabel,
            onBack = onBack,
            actions = {
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload complaint")
                }
                StatusActionMenu(
                    nextStatuses = state.nextStatuses,
                    enabled = !state.isSending && online,
                    pendingStatus = state.pendingStatus,
                    onSelect = onChangeStatus,
                )
            },
        )

        if (state.phase == ContentPhase.Loading && state.complaint == null) {
            ThreadSkeleton()
        } else {
            StateScaffold(
                phase = state.phase,
                errorMessage = state.loadError?.let(ErrorCopy::of),
                onRetry = onReload,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    state.actionError?.let { error ->
                        ActionErrorBanner(message = ErrorCopy.of(error), onDismiss = onDismissError)
                    }

                    if (state.attachments.isNotEmpty()) {
                        AttachmentsStrip(attachments = state.attachments)
                    }

                    Thread(
                        entries = state.thread,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )

                    Composer(
                        state = state,
                        online = online,
                        onReplyChange = onReplyChange,
                        onSendReply = onSendReply,
                        onAttach = onAttach,
                    )
                }
            }
        }
    }
}

// --- Thread -----------------------------------------------------------------

@Composable
private fun Thread(entries: List<ThreadEntry>, modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val listState = rememberLazyListState()

    // Keep the newest entry in view as the conversation grows.
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = spacing.screen,
            end = spacing.screen,
            top = spacing.md,
            bottom = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = modifier,
    ) {
        items(count = entries.size, key = { index -> entries[index].key }) { index ->
            when (val entry = entries[index]) {
                is ThreadEntry.Message -> MessageBubble(message = entry.message)
                is ThreadEntry.StatusEvent -> StatusEventRow(entry = entry)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ComplaintMessage) {
    val spacing = VedantTheme.spacing
    val fromStore = message.isFromStore

    val bubbleColor = if (fromStore) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (fromStore) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // A bubble wraps its text but never spans the full width, so the side it
        // sits on reads as "who spoke" at a glance.
        val maxBubbleWidth = maxWidth * 0.82f

        Row(
            horizontalArrangement = if (fromStore) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                color = bubbleColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(VedantTheme.radius.lg),
                modifier = Modifier.widthIn(max = maxBubbleWidth),
            ) {
                Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                    Text(
                        text = message.authorLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.size(spacing.xxs))
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(spacing.xxs))
                    Text(
                        text = AppClock.formatDateTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusEventRow(entry: ThreadEntry.StatusEvent) {
    val spacing = VedantTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        HorizontalDivider(
            thickness = VedantTheme.sizing.divider,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ComplaintStatusBadge(status = entry.status, rawStatus = entry.statusLabel)
            Spacer(Modifier.size(spacing.xxs))
            Text(
                text = AppClock.formatDateTime(entry.at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = VedantTheme.sizing.divider,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Attachments ------------------------------------------------------------

@Composable
private fun AttachmentsStrip(attachments: List<ComplaintAttachment>) {
    val spacing = VedantTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
    ) {
        Text(
            text = "ATTACHMENTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        attachments.forEach { attachment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (attachment.isImage) Icons.Filled.CheckCircle else Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(VedantTheme.sizing.iconMd),
                )
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// --- Composer ---------------------------------------------------------------

@Composable
private fun Composer(
    state: ComplaintThreadUiState,
    online: Boolean,
    onReplyChange: (String) -> Unit,
    onSendReply: () -> Unit,
    onAttach: (String, String, ByteArray) -> Unit,
) {
    val spacing = VedantTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canWrite = !state.isSending && online

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                // Decode + compress off the main thread; the same bucket rules
                // (5 MB, jpeg|png|webp) apply to complaint attachments.
                val payload = withContext(Dispatchers.IO) { compress(context, uri) }
                if (payload != null) onAttach(payload.fileName, payload.mimeType, payload.bytes)
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = VedantTheme.sizing.divider,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // The pending strip: unmistakable, but it never blocks reading.
            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = state.isSending,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screen, vertical = spacing.sm),
                ) {
                    val label = when {
                        state.isUploading -> "Uploading ${state.attachmentName.orEmpty()}…"
                        state.isReplyInFlight -> "Sending your reply…"
                        else -> "Updating status…"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isUploading) {
                        Spacer(Modifier.size(spacing.xs))
                        LinearProgressIndicator(
                            progress = { state.attachmentProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.canReply) {
                if (!online) {
                    Text(
                        text = "Reconnect to reply — messages are not queued while offline.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm, vertical = spacing.sm),
                ) {
                    IconButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = canWrite,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach a photo")
                    }

                    OutlinedTextField(
                        value = state.replyDraft,
                        onValueChange = onReplyChange,
                        enabled = canWrite,
                        label = { Text("Reply to the customer") },
                        isError = state.composerError != null,
                        supportingText = state.composerError?.let { message ->
                            { Text(text = message) }
                        },
                        minLines = 1,
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = VedantTheme.sizing.touchTarget),
                    )

                    IconButton(
                        onClick = onSendReply,
                        enabled = canWrite && state.replyDraft.isNotBlank(),
                    ) {
                        if (state.isReplyInFlight) {
                            CircularProgressIndicator(
                                strokeWidth = VedantTheme.sizing.progressStroke,
                                modifier = Modifier.size(VedantTheme.sizing.iconMd),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send reply",
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "This complaint is closed. Reopen it to reply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(spacing.screen),
                )
            }
        }
    }
}

/** The decoded, compressed attachment, ready to upload. */
private class AttachmentPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/**
 * Decodes and compresses a picked image with the shared [ImageProcessor], so a
 * complaint attachment obeys the same 5 MB / `jpeg|png|webp` bucket rules as a
 * product photo. Runs on `Dispatchers.IO`.
 */
private fun compress(context: Context, uri: Uri): AttachmentPayload? {
    val picked = ImageProcessor.loadAndCompress(context, uri) ?: return null
    val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "attachment.jpg"
    return AttachmentPayload(fileName = name, mimeType = picked.mimeType, bytes = picked.bytes)
}

// --- Small pieces -----------------------------------------------------------

@Composable
private fun StatusActionMenu(
    nextStatuses: List<ComplaintStatus>,
    enabled: Boolean,
    pendingStatus: ComplaintStatus?,
    onSelect: (ComplaintStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, enabled = enabled && nextStatuses.isNotEmpty()) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Change status")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        nextStatuses.forEach { status ->
            DropdownMenuItem(
                text = { Text(if (pendingStatus == status) "${status.label}…" else status.label) },
                onClick = {
                    expanded = false
                    onSelect(status)
                },
            )
        }
    }
}

@Composable
private fun ActionErrorBanner(message: String, onDismiss: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.md, end = spacing.sm, top = spacing.sm, bottom = spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(VedantTheme.sizing.iconMd),
            )
            Spacer(Modifier.size(spacing.sm))
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun ThreadSkeleton(modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.screen),
    ) {
        repeat(4) { index ->
            Row(
                horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(text = " ", onClick = {}, enabled = false)
            }
        }
    }
}
