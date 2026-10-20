package com.vedant.admin.feature.catalogue.weights

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S9 — Weight variants.
 *
 * Variants edit **inline**: tapping the pencil expands that row into a small
 * form; the "Add variant" row appends a new form at the bottom. Saving returns
 * the server-confirmed variant, which replaces (or appends) the row.
 */
@Composable
fun WeightsScreen(
    factory: ViewModelProvider.Factory,
    productId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeightsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    LaunchedEffect(productId) { viewModel.start(productId) }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Weight variants",
            subtitle = if (state.phase == ContentPhase.Data) "${state.weights.size}" else null,
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::openCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "Add variant")
                }
            },
        )

        state.actionError?.let { error ->
            InlineError(message = ErrorCopy.of(error), onDismiss = viewModel::dismissActionError)
        }

        StateScaffold(
            phase = state.phase,
            emptyTitle = "No weight variants",
            emptyMessage = "Add sizes such as 500 g or 1 kg so customers can choose.",
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
                items(items = state.weights, key = { it.id }) { weight ->
                    val editing = state.editing
                    if (editing?.id == weight.id) {
                        WeightForm(
                            edit = editing,
                            online = online,
                            onLabelChange = viewModel::onLabelChange,
                            onGramsChange = viewModel::onGramsChange,
                            onPriceChange = viewModel::onPriceChange,
                            onAvailableChange = viewModel::onAvailableChange,
                            onSave = viewModel::save,
                            onCancel = viewModel::cancelEditor,
                        )
                    } else {
                        WeightRow(
                            weight = weight,
                            isDeleting = state.deletingId == weight.id,
                            onEdit = { viewModel.openEdit(weight) },
                            onDelete = { viewModel.delete(weight.id) },
                        )
                    }
                }

                // The trailing "new variant" form.
                val editing = state.editing
                if (editing != null && editing.isCreate) {
                    item(key = "new-variant") {
                        WeightForm(
                            edit = editing,
                            online = online,
                            onLabelChange = viewModel::onLabelChange,
                            onGramsChange = viewModel::onGramsChange,
                            onPriceChange = viewModel::onPriceChange,
                            onAvailableChange = viewModel::onAvailableChange,
                            onSave = viewModel::save,
                            onCancel = viewModel::cancelEditor,
                        )
                    }
                }

                if (state.editing == null) {
                    item(key = "add-variant") {
                        SecondaryButton(
                            text = "Add variant",
                            onClick = viewModel::openCreate,
                            leadingIcon = Icons.Filled.Add,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightRow(
    weight: ProductWeight,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.lg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weight.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(spacing.xxs))
                Text(
                    text = weight.unitRateLabel ?: weight.grams?.let { "$it g" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = if (weight.isAvailable) "Available" else "Unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (weight.isAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(text = weight.price.format(), style = NumericEmphasis)
            Spacer(Modifier.size(spacing.sm))

            if (isDeleting) {
                CircularProgressIndicator(
                    strokeWidth = VedantTheme.sizing.progressStroke,
                    modifier = Modifier.size(VedantTheme.sizing.iconLg),
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${weight.label}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${weight.label}")
                }
            }
        }
    }
}

@Composable
private fun WeightForm(
    edit: WeightEditState,
    online: Boolean,
    onLabelChange: (String) -> Unit,
    onGramsChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAvailableChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Text(
                text = if (edit.isCreate) "New variant" else "Edit variant",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = edit.label,
                onValueChange = onLabelChange,
                singleLine = true,
                label = { Text("Label (e.g. 1 kg)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = edit.grams,
                    onValueChange = onGramsChange,
                    singleLine = true,
                    label = { Text("Grams") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = edit.price,
                    onValueChange = onPriceChange,
                    singleLine = true,
                    label = { Text("Price ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Available", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = edit.isAvailable, onCheckedChange = onAvailableChange)
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
                text = if (edit.isCreate) "Add variant" else "Save variant",
                onClick = onSave,
                enabled = edit.label.isNotBlank() && online,
                isInFlight = edit.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(text = "Cancel", onClick = onCancel, enabled = !edit.isSaving, modifier = Modifier.fillMaxWidth())
        }
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
