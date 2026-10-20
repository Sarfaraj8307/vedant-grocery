package com.vedant.admin.feature.catalogue.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.feature.catalogue.ProductThumb
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.DestructiveButton
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.SkeletonBlock
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every callback the editor form needs, so the content stays a pure function of its state. */
class ProductEditorActions(
    val onNameChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onCategoryChange: (String?) -> Unit,
    val onBasePriceChange: (String) -> Unit,
    val onDiscountPriceChange: (String) -> Unit,
    val onUnitChange: (String) -> Unit,
    val onUnitLabelChange: (String) -> Unit,
    val onStockChange: (String) -> Unit,
    val onQuantityLimitChange: (String) -> Unit,
    val onGlyphChange: (String) -> Unit,
    val onActiveChange: (Boolean) -> Unit,
    val onAvailabilityChange: (Boolean) -> Unit,
    val onFeaturedChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissSaveError: () -> Unit,
    val onPickGallery: () -> Unit,
    val onTakePhoto: () -> Unit,
    val onRemoveImage: () -> Unit,
    val onRetryUpload: () -> Unit,
    val onDismissUploadError: () -> Unit,
)

/**
 * S7 — Product editor.
 *
 * Grouped into **basics**, **pricing & weights**, **media** and **visibility**,
 * with a sticky save bar that reflects the pending state and the dirty state.
 * The form never clears itself on a failed save, and the image slot only shows a
 * stored image once the URL has been persisted on the product row.
 */
@Composable
fun ProductEditorScreen(
    factory: ViewModelProvider.Factory,
    productId: String?,
    onBack: () -> Unit,
    onOpenWeights: (String) -> Unit,
    onOpenOffers: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProductEditorViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(productId) { viewModel.start(productId) }
    // Re-read the variant summary when the operator returns from the weights screen.
    LaunchedEffect(state.productId) { if (state.productId != null) viewModel.refreshWeights() }

    val pick = rememberImagePickActions { uri ->
        scope.launch {
            val picked = withContext(Dispatchers.IO) { ImageProcessor.loadAndCompress(context, uri) }
            if (picked != null) {
                viewModel.onImagePicked(picked.bytes, picked.mimeType)
            } else {
                viewModel.onImageUnreadable()
            }
        }
    }

    val actions = ProductEditorActions(
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCategoryChange = viewModel::onCategoryChange,
        onBasePriceChange = viewModel::onBasePriceChange,
        onDiscountPriceChange = viewModel::onDiscountPriceChange,
        onUnitChange = viewModel::onUnitChange,
        onUnitLabelChange = viewModel::onUnitLabelChange,
        onStockChange = viewModel::onStockChange,
        onQuantityLimitChange = viewModel::onQuantityLimitChange,
        onGlyphChange = viewModel::onGlyphChange,
        onActiveChange = viewModel::onActiveChange,
        onAvailabilityChange = viewModel::onAvailabilityChange,
        onFeaturedChange = viewModel::onFeaturedChange,
        onSave = viewModel::save,
        onRetry = viewModel::reload,
        onDismissSaveError = viewModel::dismissSaveError,
        onPickGallery = pick.pickFromGallery,
        onTakePhoto = pick.takePhoto,
        onRemoveImage = viewModel::removeImage,
        onRetryUpload = viewModel::retryUpload,
        onDismissUploadError = viewModel::dismissUploadError,
    )

    ProductEditorContent(
        state = state,
        actions = actions,
        onBack = onBack,
        onOpenWeights = { state.productId?.let(onOpenWeights) },
        onOpenOffers = { state.productId?.let(onOpenOffers) },
        modifier = modifier,
    )
}

@Composable
private fun ProductEditorContent(
    state: ProductEditorUiState,
    actions: ProductEditorActions,
    onBack: () -> Unit,
    onOpenWeights: () -> Unit,
    onOpenOffers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = if (state.isCreate) "New product" else "Edit product",
            subtitle = state.draft.name.takeIf { it.isNotBlank() },
            onBack = onBack,
            actions = {
                if (state.productId != null) {
                    IconButton(onClick = onOpenOffers) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = "Manage offers")
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.phase == ContentPhase.Loading) {
                ProductEditorSkeleton()
            } else {
                StateScaffold(
                    phase = state.phase,
                    errorMessage = state.loadError?.let(ErrorCopy::of),
                    onRetry = actions.onRetry,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.screen),
                    ) {
                        BasicsSection(state = state, actions = actions)
                        PricingSection(state = state, actions = actions, onOpenWeights = onOpenWeights)
                        MediaSection(state = state, actions = actions)
                        VisibilitySection(state = state, actions = actions)
                        Spacer(Modifier.height(spacing.xxl))
                    }
                }
            }
        }

        SaveBar(state = state, actions = actions)
    }
}

// --- Sections ---------------------------------------------------------------

@Composable
private fun BasicsSection(state: ProductEditorUiState, actions: ProductEditorActions) {
    val spacing = VedantTheme.spacing
    SectionCard(title = "Basics") {
        OutlinedTextField(
            value = state.draft.name,
            onValueChange = actions.onNameChange,
            singleLine = true,
            label = { Text("Name") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(spacing.md))
        OutlinedTextField(
            value = state.draft.description.orEmpty(),
            onValueChange = actions.onDescriptionChange,
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(spacing.md))
        OutlinedTextField(
            value = state.draft.glyph.orEmpty(),
            onValueChange = actions.onGlyphChange,
            singleLine = true,
            label = { Text("Emoji / glyph") },
            supportingText = { Text("Shown when there is no photo.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(spacing.md))
        Text(
            text = "CATEGORY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(spacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = state.draft.categoryId == null,
                onClick = { actions.onCategoryChange(null) },
                label = { Text("Uncategorised") },
            )
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.draft.categoryId == category.id,
                    onClick = { actions.onCategoryChange(category.id) },
                    label = { Text("${category.displayGlyph} ${category.name}") },
                )
            }
        }
    }
}

@Composable
private fun PricingSection(
    state: ProductEditorUiState,
    actions: ProductEditorActions,
    onOpenWeights: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    SectionCard(title = "Pricing & weights") {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = state.basePriceText,
                onValueChange = actions.onBasePriceChange,
                singleLine = true,
                label = { Text("Base price ₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.priceError != null,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.discountPriceText,
                onValueChange = actions.onDiscountPriceChange,
                singleLine = true,
                label = { Text("Discount ₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        state.priceError?.let { message ->
            Spacer(Modifier.size(spacing.xs))
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = state.draft.unit.orEmpty(),
                onValueChange = actions.onUnitChange,
                singleLine = true,
                label = { Text("Unit") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.draft.unitLabel.orEmpty(),
                onValueChange = actions.onUnitLabelChange,
                singleLine = true,
                label = { Text("Unit label") },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = state.stockText,
                onValueChange = actions.onStockChange,
                singleLine = true,
                label = { Text("Stock") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.quantityLimitText,
                onValueChange = actions.onQuantityLimitChange,
                singleLine = true,
                label = { Text("Order limit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.size(spacing.lg))
        WeightSummary(state = state, onOpenWeights = onOpenWeights)
    }
}

@Composable
private fun WeightSummary(state: ProductEditorUiState, onOpenWeights: () -> Unit) {
    val spacing = VedantTheme.spacing
    if (state.isCreate) {
        Text(
            text = "Save the product to add weight variants.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        Text(
            text = when (state.weights.size) {
                0 -> "No weight variants yet."
                1 -> "1 weight variant"
                else -> "${state.weights.size} weight variants"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.weights.isNotEmpty()) {
            Spacer(Modifier.size(spacing.xs))
            state.weights.take(3).forEach { weight ->
                Text(
                    text = "• ${weight.label} — ${weight.price.format()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(spacing.sm))
        SecondaryButton(text = "Manage variants", onClick = onOpenWeights)
    }
}

@Composable
private fun MediaSection(state: ProductEditorUiState, actions: ProductEditorActions) {
    val spacing = VedantTheme.spacing
    SectionCard(title = "Photo") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductThumb(
                imageUrl = state.draft.imageUrl,
                glyph = state.draft.glyph?.takeIf { it.isNotBlank() }
                    ?: state.draft.name.take(1).uppercase().ifBlank { "?" },
                size = VedantTheme.sizing.previewImage,
            )
            Spacer(Modifier.size(spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SecondaryButton(
                    text = "Choose photo",
                    onClick = actions.onPickGallery,
                    leadingIcon = Icons.Filled.PhotoLibrary,
                    enabled = !state.isUploading,
                )
                SecondaryButton(
                    text = "Take photo",
                    onClick = actions.onTakePhoto,
                    leadingIcon = Icons.Filled.PhotoCamera,
                    enabled = !state.isUploading,
                )
            }
        }

        if (state.isCreate) {
            Spacer(Modifier.size(spacing.md))
            Text(
                text = "Save the product before adding a photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val upload = state.upload) {
            is UploadState.InFlight -> {
                Spacer(Modifier.size(spacing.md))
                LinearProgressIndicator(
                    progress = { upload.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = "Uploading… ${(upload.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UploadState.Failed -> {
                Spacer(Modifier.size(spacing.md))
                UploadErrorBanner(
                    message = ErrorCopy.of(upload.error),
                    onRetry = actions.onRetryUpload,
                    onDismiss = actions.onDismissUploadError,
                )
            }

            UploadState.Idle -> {
                if (state.hasImage) {
                    Spacer(Modifier.size(spacing.md))
                    SecondaryButton(text = "Remove photo", onClick = actions.onRemoveImage)
                }
            }
        }
    }
}

@Composable
private fun VisibilitySection(state: ProductEditorUiState, actions: ProductEditorActions) {
    SectionCard(title = "Visibility") {
        SettingRow(
            label = "Listed",
            description = "Hidden products are not shown to customers.",
            checked = state.draft.isActive,
            onCheckedChange = actions.onActiveChange,
        )
        SettingRow(
            label = "Available",
            description = "Unavailable products show as out of stock.",
            checked = state.draft.availability,
            onCheckedChange = actions.onAvailabilityChange,
        )
        SettingRow(
            label = "Featured",
            description = "Featured products are highlighted on the storefront.",
            checked = state.draft.isFeatured,
            onCheckedChange = actions.onFeaturedChange,
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = VedantTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(spacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// --- Save bar ---------------------------------------------------------------

@Composable
private fun SaveBar(state: ProductEditorUiState, actions: ProductEditorActions) {
    val spacing = VedantTheme.spacing
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = VedantTheme.sizing.cardShadow) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            (state.saveState as? ActionState.Failed)?.let { failure ->
                UploadErrorBanner(
                    message = ErrorCopy.of(failure.error),
                    onRetry = actions.onSave,
                    onDismiss = actions.onDismissSaveError,
                )
                Spacer(Modifier.size(spacing.sm))
            }

            Text(
                text = when {
                    state.isSaving -> "Saving…"
                    state.saveState is ActionState.Failed -> "Not saved — your changes are safe."
                    state.isDirty -> "Unsaved changes"
                    state.savedOnce -> "Saved"
                    else -> "Up to date"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!online) {
                Spacer(Modifier.size(spacing.xxs))
                OfflineHint(text = "Reconnect to save — changes are not queued while offline.")
            }
            Spacer(Modifier.size(spacing.sm))
            PrimaryButton(
                text = if (state.isCreate) "Create product" else "Save changes",
                onClick = actions.onSave,
                enabled = state.canSave && online,
                isInFlight = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UploadErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        color = DangerBg,
        contentColor = Danger,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.md),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(spacing.sm))
            SecondaryButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Filled.Refresh,
            )
            Spacer(Modifier.size(spacing.sm))
            DestructiveButton(text = "Dismiss", onClick = onDismiss)
        }
    }
}

// --- Skeleton ---------------------------------------------------------------

@Composable
private fun ProductEditorSkeleton() {
    val spacing = VedantTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.screen),
    ) {
        repeat(2) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(VedantTheme.sizing.skeletonHero))
        }
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(VedantTheme.sizing.skeletonPanel))
    }
}
