package com.vedant.admin.feature.catalogue.categories

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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.vedant.admin.domain.model.Category
import com.vedant.admin.ui.components.BadgeTone
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.StatusBadge
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S8 — Categories.
 *
 * A simple ordered list; tapping a row opens an add/edit sheet. The sheet's Save
 * is blocked while in flight, and a delete always asks first. Nothing on this
 * screen is patched locally — the list is re-read from the server after a write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CategoriesViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Categories",
            subtitle = if (state.phase == ContentPhase.Data) "${state.categories.size}" else null,
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::openCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "Add category")
                }
            },
        )

        state.actionError?.let { error ->
            InlineError(message = ErrorCopy.of(error), onDismiss = viewModel::dismissActionError)
        }

        StateScaffold(
            phase = state.phase,
            emptyIcon = Icons.Filled.Category,
            emptyTitle = "No categories yet",
            emptyMessage = "Add a category to group your products.",
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
                items(items = state.categories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        isDeleting = state.deletingId == category.id,
                        actionsEnabled = online,
                        onEdit = { viewModel.openEdit(category) },
                        onDelete = { viewModel.requestDelete(category) },
                    )
                }
            }
        }
    }

    state.edit?.let { edit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissEditor() },
            sheetState = sheetState,
        ) {
            CategoryEditSheet(
                edit = edit,
                online = online,
                onNameChange = viewModel::onNameChange,
                onGlyphChange = viewModel::onGlyphChange,
                onSortOrderChange = viewModel::onSortOrderChange,
                onActiveChange = viewModel::onActiveChange,
                onSave = viewModel::save,
                onDismiss = viewModel::dismissEditor,
            )
        }
    }

    state.pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete category?") },
            text = { Text("“${category.name}” will be removed. Products in it become uncategorised.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isDeleting: Boolean,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        onClick = onEdit,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.lg),
        ) {
            Text(text = category.displayGlyph, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        label = if (category.isActive) "Active" else "Hidden",
                        tone = if (category.isActive) BadgeTone.Success else BadgeTone.Neutral,
                        icon = if (category.isActive) Icons.Filled.CheckCircle else Icons.Filled.VisibilityOff,
                    )
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = "order ${category.sortOrder}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isDeleting) {
                CircularProgressIndicator(
                    strokeWidth = VedantTheme.sizing.progressStroke,
                    modifier = Modifier.size(VedantTheme.sizing.iconLg),
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${category.name}")
                }
                IconButton(onClick = onDelete, enabled = actionsEnabled) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
                }
            }
        }
    }
}

@Composable
private fun CategoryEditSheet(
    edit: CategoryEditState,
    online: Boolean,
    onNameChange: (String) -> Unit,
    onGlyphChange: (String) -> Unit,
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
            text = if (edit.isCreate) "New category" else "Edit category",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = edit.name,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text("Name") },
            isError = edit.nameError != null,
            supportingText = edit.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = edit.glyph,
                onValueChange = onGlyphChange,
                singleLine = true,
                label = { Text("Emoji / glyph") },
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

        edit.error?.let { error ->
            Text(
                text = ErrorCopy.of(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!online) {
            OfflineHint(text = "Reconnect to save — changes are not queued while offline.")
        }

        PrimaryButton(
            text = if (edit.isCreate) "Create category" else "Save changes",
            onClick = onSave,
            enabled = edit.name.isNotBlank() && online,
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
