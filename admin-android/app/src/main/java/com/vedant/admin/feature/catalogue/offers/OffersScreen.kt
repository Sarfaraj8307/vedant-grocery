package com.vedant.admin.feature.catalogue.offers

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.ui.components.BadgeTone
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.DateField
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.StatusBadge
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.datetime.Instant

/**
 * S10 — Offers.
 *
 * Each offer shows its **date range** and a status word (`Active`, `Scheduled`,
 * `Expired`, `Disabled`) so the operator can tell at a glance which offers are
 * live. The window is entered with the Material 3 date picker and stored as an
 * IST start-of-day instant, matching the rest of the app's time handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(
    factory: ViewModelProvider.Factory,
    productId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OffersViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    LaunchedEffect(productId) { viewModel.start(productId) }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Offers",
            subtitle = if (state.phase == ContentPhase.Data) "${state.offers.size}" else null,
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::openCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "Add offer")
                }
            },
        )

        state.actionError?.let { error ->
            InlineError(message = ErrorCopy.of(error), onDismiss = viewModel::dismissActionError)
        }

        StateScaffold(
            phase = state.phase,
            emptyIcon = Icons.Filled.LocalOffer,
            emptyTitle = "No offers yet",
            emptyMessage = "Add a discount or a special price for this product.",
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
                if (!online) {
                    item(key = "offline-hint") {
                        OfflineHint(text = "Editing and deleting need a connection.")
                    }
                }
                items(items = state.offers, key = { it.id }) { offer ->
                    OfferRow(
                        offer = offer,
                        now = state.now,
                        isDeleting = state.deletingId == offer.id,
                        actionsEnabled = online,
                        onEdit = { viewModel.openEdit(offer) },
                        onDelete = { viewModel.delete(offer.id) },
                    )
                }
            }
        }
    }

    state.editor?.let { edit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::dismissEditor, sheetState = sheetState) {
            OfferEditSheet(
                edit = edit,
                online = online,
                onTitleChange = viewModel::onTitleChange,
                onDiscountChange = viewModel::onDiscountChange,
                onPriceChange = viewModel::onPriceChange,
                onStartsAtChange = viewModel::onStartsAtChange,
                onEndsAtChange = viewModel::onEndsAtChange,
                onActiveChange = viewModel::onActiveChange,
                onSave = viewModel::save,
                onDismiss = viewModel::dismissEditor,
            )
        }
    }
}

@Composable
private fun OfferRow(
    offer: Offer,
    now: Instant,
    isDeleting: Boolean,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.lg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = offer.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(spacing.sm))
                    StatusBadge(
                        label = offer.statusLabel(now),
                        tone = offer.statusTone(now),
                        icon = offer.statusIcon(now),
                    )
                }
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = offer.valueLabel,
                    style = NumericEmphasis,
                )
                Spacer(Modifier.size(spacing.xxs))
                Text(
                    text = offer.rangeLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(spacing.sm))

            if (isDeleting) {
                CircularProgressIndicator(
                    strokeWidth = VedantTheme.sizing.progressStroke,
                    modifier = Modifier.size(VedantTheme.sizing.iconLg),
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${offer.title}")
                }
                IconButton(onClick = onDelete, enabled = actionsEnabled) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${offer.title}")
                }
            }
        }
    }
}

@Composable
private fun OfferEditSheet(
    edit: OfferEditState,
    online: Boolean,
    onTitleChange: (String) -> Unit,
    onDiscountChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onStartsAtChange: (Instant?) -> Unit,
    onEndsAtChange: (Instant?) -> Unit,
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
            text = if (edit.isCreate) "New offer" else "Edit offer",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = edit.title,
            onValueChange = onTitleChange,
            singleLine = true,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = edit.discountPercent,
                onValueChange = onDiscountChange,
                singleLine = true,
                label = { Text("Discount %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = edit.price,
                onValueChange = onPriceChange,
                singleLine = true,
                label = { Text("Offer price ₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "VALID FROM",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            DateField(
                label = "Start date",
                instant = edit.startsAt,
                onPick = onStartsAtChange,
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = "End date",
                instant = edit.endsAt,
                onPick = onEndsAtChange,
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Enabled", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
            text = if (edit.isCreate) "Create offer" else "Save changes",
            onClick = onSave,
            enabled = edit.title.isNotBlank() && online,
            isInFlight = edit.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(text = "Cancel", onClick = onDismiss, enabled = !edit.isSaving, modifier = Modifier.fillMaxWidth())
    }
}

private fun Offer.rangeLabel(): String = when {
    startsAt != null && endsAt != null -> "${AppClock.formatDate(startsAt)} – ${AppClock.formatDate(endsAt)}"
    startsAt != null -> "From ${AppClock.formatDate(startsAt)}"
    endsAt != null -> "Until ${AppClock.formatDate(endsAt)}"
    else -> "No end date"
}

private fun Offer.statusTone(now: Instant): BadgeTone = when {
    !isActive -> BadgeTone.Neutral
    isExpired(now) -> BadgeTone.Danger
    isScheduled(now) -> BadgeTone.Info
    else -> BadgeTone.Success
}

private fun Offer.statusIcon(now: Instant) = when {
    !isActive -> Icons.Filled.VisibilityOff
    isExpired(now) -> Icons.Filled.HourglassEmpty
    isScheduled(now) -> Icons.Filled.EventAvailable
    else -> Icons.Filled.CheckCircle
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
