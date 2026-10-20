package com.vedant.admin.feature.orders.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderItem
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.usecase.OrderTransitions
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.DestructiveButton
import com.vedant.admin.ui.components.DetailRow
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OrderStatusBadge
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.SkeletonBlock
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S6 — Order detail.
 *
 * A receipt-like view of one order: the header, the line items (with weights and
 * per-line totals), the totals, the delivery details and the status timeline —
 * followed by a pinned action bar offering the workflow's next steps.
 *
 * ### The three non-negotiables, made visible
 *  - **Server truth only** — every amount, including `final_total`, is rendered
 *    straight from the re-fetched order; nothing here recomputes money.
 *  - **Only the offered transitions** — the action bar is built from
 *    [OrderDetailUiState.nextStatuses] (a convenience over the server's real gate).
 *  - **No moving on rejection** — a failed status change surfaces its mapped copy
 *    in a banner and leaves the rendered order exactly as it was.
 */
@Composable
fun OrderDetailScreen(
    factory: ViewModelProvider.Factory,
    orderId: String,
    onBack: () -> Unit,
    onOpenComplaint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OrderDetailViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(orderId) { viewModel.start(orderId) }

    OrderDetailContent(
        state = state,
        onBack = onBack,
        onReload = viewModel::reload,
        onAdvance = viewModel::advanceTo,
        onOpenRejectSheet = viewModel::openRejectSheet,
        onRejectReasonChange = viewModel::onRejectReasonChange,
        onDismissRejectSheet = viewModel::dismissRejectSheet,
        onSubmitReject = viewModel::submitReject,
        onRestoreItem = viewModel::restoreItem,
        onDismissActionError = viewModel::dismissActionError,
        onOpenComplaint = onOpenComplaint,
        modifier = modifier,
    )
}

@Composable
private fun OrderDetailContent(
    state: OrderDetailUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onAdvance: (OrderStatus, String?) -> Unit,
    onOpenRejectSheet: (OrderItem) -> Unit,
    onRejectReasonChange: (String) -> Unit,
    onDismissRejectSheet: () -> Unit,
    onSubmitReject: () -> Unit,
    onRestoreItem: (String) -> Unit,
    onDismissActionError: () -> Unit,
    onOpenComplaint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing

    // Which status the operator is confirming, when that status needs a reason.
    var cancelTarget by remember { mutableStateOf<OrderStatus?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = state.order?.let { "#${it.shortId}" } ?: "Order",
            subtitle = state.order?.statusLabel,
            onBack = onBack,
            actions = {
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload order")
                }
            },
        )

        if (state.phase == ContentPhase.Loading && state.order == null) {
            // Skeleton, not a spinner: the receipt's shape appears immediately.
            OrderDetailSkeleton()
        } else {
            StateScaffold(
                phase = state.phase,
                errorMessage = state.loadError?.let(ErrorCopy::of),
                onRetry = onReload,
            ) {
                val order = state.order
                if (order != null) {
                    OrderDetailBody(
                        state = state,
                        order = order,
                        onAdvance = onAdvance,
                        onOpenRejectSheet = onOpenRejectSheet,
                        onRestoreItem = onRestoreItem,
                        onDismissActionError = onDismissActionError,
                        onOpenComplaint = onOpenComplaint,
                        onRequestCancel = { cancelTarget = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // The cancellation reason is collected before the request, so a required
    // reason is never silently omitted (the server would refuse it anyway).
    cancelTarget?.let { target ->
        CancelReasonDialog(
            onDismiss = { cancelTarget = null },
            onConfirm = { reason ->
                cancelTarget = null
                onAdvance(target, reason)
            },
        )
    }

    // The reject sheet is an overlay, rendered above the pinned action bar.
    state.rejectSheet?.let { sheet ->
        RejectItemSheet(
            state = sheet,
            onReasonChange = onRejectReasonChange,
            onDismiss = onDismissRejectSheet,
            onSubmit = onSubmitReject,
        )
    }
}

@Composable
private fun OrderDetailBody(
    state: OrderDetailUiState,
    order: Order,
    onAdvance: (OrderStatus, String?) -> Unit,
    onOpenRejectSheet: (OrderItem) -> Unit,
    onRestoreItem: (String) -> Unit,
    onDismissActionError: () -> Unit,
    onOpenComplaint: (String) -> Unit,
    onRequestCancel: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val statusError = (state.statusAction as? ActionState.Failed)?.error
    val actionError = statusError ?: state.itemActionError
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    Column(modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.md,
                bottom = spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item(key = "header") { OrderHeaderCard(order) }

            item(key = "items") {
                ItemsSection(
                    order = order,
                    adjustable = state.itemsAdjustable,
                    actionsEnabled = online,
                    inFlightItemId = state.itemActionInFlightId,
                    onReject = onOpenRejectSheet,
                    onRestore = onRestoreItem,
                )
            }

            item(key = "totals") { TotalsSection(order) }

            item(key = "delivery") { DeliverySection(order) }

            if (order.history.isNotEmpty()) {
                item(key = "timeline") { TimelineSection(order.history) }
            }

            item(key = "complaint") {
                ComplaintLink(onClick = { onOpenComplaint(order.id) })
            }
        }

        // The failure banner is pinned above the actions (not buried in the
        // scroll) so a rejected change is impossible to miss.
        if (actionError != null) {
            ActionErrorBanner(
                message = ErrorCopy.of(actionError),
                onDismiss = onDismissActionError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screen, vertical = spacing.sm),
            )
        }

        StatusActionBar(
            statuses = state.nextStatuses,
            inFlight = state.isStatusInFlight,
            pendingStatus = state.pendingStatus,
            enabled = online,
            onSelect = { status ->
                if (OrderTransitions.requiresReason(status)) onRequestCancel(status)
                else onAdvance(status, null)
            },
        )
    }
}

// --- Sections ---------------------------------------------------------------

@Composable
private fun OrderHeaderCard(order: Order) {
    val spacing = VedantTheme.spacing
    SectionCard(title = "Order") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderStatusBadge(status = order.status, rawStatus = order.statusRaw)
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = order.createdAt?.let(AppClock::formatDateTime) ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(spacing.sm))
        DetailRow(label = "Reference", value = "#${order.shortId}")
        DetailRow(label = "Items", value = order.itemCount.toString())
        order.updatedAt?.let { DetailRow(label = "Last updated", value = AppClock.formatDateTime(it)) }
    }
}

@Composable
private fun ItemsSection(
    order: Order,
    adjustable: Boolean,
    actionsEnabled: Boolean,
    inFlightItemId: String?,
    onReject: (OrderItem) -> Unit,
    onRestore: (String) -> Unit,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    SectionCard(title = "Items (${order.itemCount})") {
        if (order.items.isEmpty()) {
            Text(
                text = "No line items on this order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            order.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = sizing.divider,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = spacing.sm),
                    )
                }
                OrderItemRow(
                    item = item,
                    adjustable = adjustable,
                    actionsEnabled = actionsEnabled,
                    inFlight = inFlightItemId == item.id,
                    onReject = onReject,
                    onRestore = onRestore,
                )
            }
        }
    }
}

@Composable
private fun OrderItemRow(
    item: OrderItem,
    adjustable: Boolean,
    actionsEnabled: Boolean,
    inFlight: Boolean,
    onReject: (OrderItem) -> Unit,
    onRestore: (String) -> Unit,
) {
    val spacing = VedantTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.rejected) TextDecoration.LineThrough else null,
                    color = if (item.rejected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                val meta = buildList {
                    item.weightLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add("${item.qty} × ${item.unitPrice.format()}")
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (item.rejected && !item.rejectionReason.isNullOrBlank()) {
                    Text(
                        text = "Rejected: ${item.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.size(spacing.md))

            Text(text = item.lineTotal.format(), style = NumericEmphasis)
        }

        if (adjustable) {
            Spacer(Modifier.size(spacing.sm))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (item.rejected) {
                    SecondaryButton(
                        text = "Restore",
                        onClick = { onRestore(item.id) },
                        enabled = actionsEnabled,
                        isInFlight = inFlight,
                        leadingIcon = Icons.Filled.Restore,
                    )
                } else {
                    DestructiveButton(
                        text = "Reject",
                        onClick = { onReject(item) },
                        enabled = actionsEnabled,
                        leadingIcon = Icons.Filled.Delete,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsSection(order: Order) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    SectionCard(title = "Totals") {
        order.subtotal?.let { DetailRow(label = "Subtotal", value = it.format()) }
        DetailRow(label = "Delivery", value = order.deliveryCharge.format())
        DetailRow(label = "Original total", value = order.originalTotal.format())

        if (order.hasAdjustment) {
            DetailRow(label = "Adjustment", value = order.adjustmentAmount.format())
            order.adjustmentReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            thickness = sizing.divider,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = spacing.sm),
        )
        DetailRow(label = "Final total", value = order.finalTotal.format(), emphasise = true)
        Text(
            text = "Charged amount, as confirmed by the server.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeliverySection(order: Order) {
    val spacing = VedantTheme.spacing
    SectionCard(title = "Delivery") {
        DetailRow(
            label = "Customer",
            value = order.customerName?.takeIf { it.isNotBlank() } ?: order.customerRef ?: "—",
        )
        order.customerPhone?.let { phone ->
            DetailRow(label = "Phone", value = phone)
        }
        order.deliverySlotId?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = "Slot", value = it)
        }

        if (order.addressLines.isEmpty()) {
            DetailRow(label = "Address", value = "—")
        } else {
            Text(
                text = "Address",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(spacing.xxs))
            order.addressLines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // CALL CUSTOMER (§12). ACTION_DIAL opens the dialer pre-filled — it never
        // places the call itself, so the operator always confirms. Hidden entirely
        // when the snapshot carried no phone, rather than showing a dead button.
        order.customerPhone?.let { phone ->
            Spacer(Modifier.size(spacing.sm))
            CallCustomerButton(phone = phone)
        }
    }
}

/**
 * Opens the system dialer with [phone] pre-filled.
 *
 * `ACTION_DIAL` is deliberate: `ACTION_CALL` would place the call immediately and
 * needs the `CALL_PHONE` runtime permission, which this app must not hold. If no
 * dialer exists (rare — a device with telephony stripped), the tap is a no-op
 * rather than a crash.
 */
@Composable
private fun CallCustomerButton(phone: String) {
    val context = LocalContext.current
    SecondaryButton(
        text = "Call customer",
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.fromParts("tel", phone, null)
            }
            runCatching { context.startActivity(intent) }
        },
        leadingIcon = Icons.Filled.Phone,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimelineSection(history: List<OrderStatusEvent>) {
    // Oldest first, so the receipt reads top-to-bottom; a null timestamp sorts
    // last rather than jumping the order around.
    val events = remember(history) {
        history.sortedWith(compareBy(nullsLast<kotlinx.datetime.Instant>()) { it.createdAt })
    }
    SectionCard(title = "Timeline") {
        events.forEachIndexed { index, event ->
            TimelineRow(event = event, isLast = index == events.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(event: OrderStatusEvent, isLast: Boolean) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // The rail: a dot, then a connector down to the next entry.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(sizing.iconMd)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .size(spacing.sm)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(sizing.divider)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Spacer(Modifier.size(spacing.md))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) spacing.none else spacing.lg),
        ) {
            Text(
                text = event.toStatus?.label ?: event.toStatusRaw,
                style = MaterialTheme.typography.titleSmall,
            )
            event.createdAt?.let {
                Text(
                    text = AppClock.formatDateTime(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.reason?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComplaintLink(onClick: () -> Unit) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.lg, end = spacing.sm, top = spacing.sm, bottom = spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.iconMd),
            )
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = "Something wrong with this order?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text("Raise a complaint") }
        }
    }
}

@Composable
private fun ActionErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = spacing.md, end = spacing.sm, top = spacing.sm, bottom = spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(sizing.iconMd),
            )
            Spacer(Modifier.size(spacing.sm))
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/**
 * The pinned action bar. It offers **only** the statuses the client is willing to
 * send; a cancellation is routed through the reason dialog first. While a change
 * is in flight every action is disabled, and the one being applied shows the
 * spinner — a double-submit is impossible.
 */
@Composable
private fun StatusActionBar(
    statuses: List<OrderStatus>,
    inFlight: Boolean,
    pendingStatus: OrderStatus?,
    enabled: Boolean,
    onSelect: (OrderStatus) -> Unit,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = sizing.divider,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(modifier = Modifier.padding(spacing.screen)) {
                if (statuses.isEmpty()) {
                    Text(
                        text = "No further actions — this order is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (!enabled) {
                        Text(
                            text = "Reconnect to change this order's status.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = spacing.sm),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        statuses.forEach { status ->
                            val isPending = inFlight && pendingStatus == status
                            if (status == OrderStatus.CANCELLED) {
                                DestructiveButton(
                                    text = status.actionLabel(),
                                    onClick = { onSelect(status) },
                                    enabled = enabled && !inFlight,
                                    isInFlight = isPending,
                                    leadingIcon = Icons.Filled.Cancel,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                PrimaryButton(
                                    text = status.actionLabel(),
                                    onClick = { onSelect(status) },
                                    enabled = enabled && !inFlight,
                                    isInFlight = isPending,
                                    leadingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val spacing = VedantTheme.spacing
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this order?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = "The customer is told why, and the reason is recorded in the order timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DestructiveButton(
                text = "Cancel order",
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank(),
            )
        },
        dismissButton = {
            SecondaryButton(text = "Keep order", onClick = onDismiss)
        },
    )
}

@Composable
private fun OrderDetailSkeleton(modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.screen),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(sizing.touchTarget * 2))
        repeat(3) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(sizing.touchTarget * 3))
        }
    }
}

// --- Small helpers ----------------------------------------------------------

/** The workflow's action wording for a target status. */
private fun OrderStatus.actionLabel(): String = when (this) {
    OrderStatus.PENDING -> "Reopen order"
    OrderStatus.CONFIRMED -> "Accept order"
    OrderStatus.PROCESSING -> "Start packing"
    OrderStatus.READY_FOR_DELIVERY -> "Mark ready"
    OrderStatus.OUT_FOR_DELIVERY -> "Dispatch"
    OrderStatus.DELIVERED -> "Mark delivered"
    OrderStatus.CANCELLED -> "Cancel order"
}

/** Short, human-typable handle for the order — matches the list's `shortId`. */
private val Order.shortId: String
    get() = id.substringAfterLast('-').ifBlank { id }.take(8).uppercase()
