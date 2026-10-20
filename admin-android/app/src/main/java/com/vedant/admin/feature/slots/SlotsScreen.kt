package com.vedant.admin.feature.slots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.ui.components.BadgeTone
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.DateField
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.OrderListSkeleton
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.StatusBadge
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

/**
 * S11 — Delivery slots.
 *
 * Reads like a day schedule: each calendar day is a header, and under it each
 * slot shows its window and a utilisation bar. A slot that is **full** is
 * visually distinct (an error-coloured bar and a `Full` pill) so the operator
 * can spot a sold-out window without reading the numbers.
 *
 * When `admin_slot_load` reports nothing for a slot, the row shows its capacity
 * instead of an invented bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotsScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SlotsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Delivery slots",
            subtitle = if (state.phase == ContentPhase.Data) "Next ${state.days} days" else null,
            onBack = onBack,
            actions = {
                if (state.isEnsuring) {
                    CircularProgressIndicator(
                        strokeWidth = VedantTheme.sizing.progressStroke,
                        modifier = Modifier
                            .size(VedantTheme.sizing.iconLg)
                            .padding(VedantTheme.spacing.sm),
                    )
                } else {
                    IconButton(onClick = viewModel::ensureSlots) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Generate recurring slots")
                    }
                }
                IconButton(onClick = viewModel::openCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "Add slot")
                }
            },
        )

        state.actionError?.let { error ->
            InlineError(message = ErrorCopy.of(error), onDismiss = viewModel::dismissActionError)
        }

        if (state.phase == ContentPhase.Loading) {
            OrderListSkeleton()
        } else {
            StateScaffold(
                phase = state.phase,
                emptyTitle = "No delivery slots",
                emptyMessage = "Generate the recurring slots, or add one for a specific day.",
                errorMessage = state.loadError?.let(ErrorCopy::of),
                onRetry = viewModel::reload,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = VedantTheme.spacing.screen,
                        end = VedantTheme.spacing.screen,
                        top = VedantTheme.spacing.sm,
                        bottom = VedantTheme.spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VedantTheme.spacing.rowGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items = state.slots, key = { _, slot -> slot.id }) { index, slot ->
                        val showHeader = index == 0 || state.slots[index - 1].dayLabel != slot.dayLabel
                        Column {
                            if (showHeader) DayHeader(slot.dayLabel)
                            SlotCard(
                                slot = slot,
                                load = state.loadFor(slot.id),
                                isDeleting = state.deletingId == slot.id,
                                onEdit = { viewModel.openEdit(slot) },
                                onDelete = { viewModel.delete(slot.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { edit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::cancelEditor, sheetState = sheetState) {
            SlotEditSheet(
                edit = edit,
                online = online,
                onNameChange = viewModel::onNameChange,
                onDateChange = viewModel::onDateChange,
                onStartTimeChange = viewModel::onStartTimeChange,
                onEndTimeChange = viewModel::onEndTimeChange,
                onCapacityChange = viewModel::onCapacityChange,
                onSortOrderChange = viewModel::onSortOrderChange,
                onActiveChange = viewModel::onActiveChange,
                onSave = viewModel::save,
                onDismiss = viewModel::cancelEditor,
            )
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    val spacing = VedantTheme.spacing
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xxs),
    )
}

@Composable
private fun SlotCard(
    slot: DeliverySlot,
    load: SlotLoad?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(spacing.xxs))
                    Text(
                        text = "Capacity ${slot.capacity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (load?.isFull == true) {
                    StatusBadge(label = "Full", tone = BadgeTone.Danger, icon = Icons.Filled.Cancel)
                    Spacer(Modifier.size(spacing.sm))
                }

                if (isDeleting) {
                    CircularProgressIndicator(
                        strokeWidth = VedantTheme.sizing.progressStroke,
                        modifier = Modifier.size(VedantTheme.sizing.iconLg),
                    )
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${slot.title}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${slot.title}")
                    }
                }
            }

            Spacer(Modifier.size(spacing.sm))

            if (load != null) {
                LinearProgressIndicator(
                    progress = { load.utilisation },
                    color = if (load.isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(spacing.xs))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${load.booked} booked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (load.isFull) "No places left" else "${load.remaining} left · ${load.utilisationLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "No bookings reported yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlotEditSheet(
    edit: SlotEditState,
    online: Boolean,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onCapacityChange: (String) -> Unit,
    onSortOrderChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen)
            .padding(bottom = spacing.xxl),
    ) {
        Text(
            text = if (edit.isCreate) "New slot" else "Edit slot",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = edit.name,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text("Name (e.g. Morning)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "DAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateField(
            label = "Pick a day",
            instant = edit.date?.atStartOfDayIn(AppClock.IST),
            onPick = { picked -> onDateChange(picked?.let { AppClock.toIst(it).date }) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = edit.startTime,
                onValueChange = onStartTimeChange,
                singleLine = true,
                label = { Text("Start (HH:mm)") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = edit.endTime,
                onValueChange = onEndTimeChange,
                singleLine = true,
                label = { Text("End (HH:mm)") },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = edit.capacity,
                onValueChange = onCapacityChange,
                singleLine = true,
                label = { Text("Capacity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = edit.sortOrder,
                onValueChange = onSortOrderChange,
                singleLine = true,
                label = { Text("Sort order") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Active", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = edit.isActive, onCheckedChange = onActiveChange)
        }

        edit.fieldError?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        edit.error?.let { error ->
            Text(text = ErrorCopy.of(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (!online) {
            OfflineHint(text = "Reconnect to save — changes are not queued while offline.")
        }

        PrimaryButton(
            text = if (edit.isCreate) "Create slot" else "Save changes",
            onClick = onSave,
            enabled = edit.capacity.isNotBlank() && online,
            isInFlight = edit.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(text = "Cancel", onClick = onDismiss, enabled = !edit.isSaving, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InlineError(message: String, onDismiss: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.md),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(spacing.sm))
            SecondaryButton(text = "Dismiss", onClick = onDismiss)
        }
    }
}
