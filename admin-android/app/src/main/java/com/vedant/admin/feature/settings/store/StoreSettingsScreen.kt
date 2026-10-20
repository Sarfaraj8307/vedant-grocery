package com.vedant.admin.feature.settings.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme
import com.vedant.admin.ui.theme.Warning
import com.vedant.admin.ui.theme.WarningBg

/**
 * S12 — Store settings.
 *
 * Grouped and calm rather than a wall of switches: **Store** (name, phone,
 * WhatsApp), **Opening**, then **Delivery & order limits**. Each group is a
 * labelled card, so the screen reads top-to-bottom as a short checklist.
 *
 * ### The `store_name` mismatch is made visible, not fixed
 * The live row reads **"Fresh Mart"** on a store branded *Vedant Fruit &
 * Vegetables* (PRD R6). The screen shows the value **exactly as the server has
 * it** — it is never silently rewritten — and raises a warning card above the
 * field so the owner sees the problem and can correct it deliberately.
 */
@Composable
fun StoreSettingsScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StoreSettingsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    StoreSettingsContent(
        state = state,
        onBack = onBack,
        onReload = viewModel::reload,
        onStoreNameChange = viewModel::onStoreNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onWhatsappChange = viewModel::onWhatsappChange,
        onOpenChange = viewModel::onOpenChange,
        onDeliveryFeeChange = viewModel::onDeliveryFeeChange,
        onFreeDeliveryThresholdChange = viewModel::onFreeDeliveryThresholdChange,
        onMinOrderAmountChange = viewModel::onMinOrderAmountChange,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun StoreSettingsContent(
    state: StoreSettingsUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStoreNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onWhatsappChange: (String) -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onDeliveryFeeChange: (String) -> Unit,
    onFreeDeliveryThresholdChange: (String) -> Unit,
    onMinOrderAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    // A settings change is authoritative: offline, the save is disabled, not queued.
    val online = LocalOnlineStatus.current

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Store settings",
            subtitle = if (state.isDirty) "Unsaved changes" else null,
            onBack = onBack,
        )

        StateScaffold(
            phase = state.phase,
            errorMessage = state.loadError?.let(ErrorCopy::of),
            onRetry = onReload,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.screen),
                ) {
                    if (state.nameMismatch) StoreNameWarning(storeName = state.form.storeName)

                    SectionCard(title = "Store") {
                        OutlinedTextField(
                            value = state.form.storeName,
                            onValueChange = onStoreNameChange,
                            singleLine = true,
                            label = { Text("Store name") },
                            isError = state.errorFor(StoreSettingsField.STORE_NAME) != null,
                            supportingText = state.errorFor(StoreSettingsField.STORE_NAME)?.let { message ->
                                { Text(message) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(spacing.md))
                        OutlinedTextField(
                            value = state.form.phone,
                            onValueChange = onPhoneChange,
                            singleLine = true,
                            label = { Text("Phone") },
                            isError = state.errorFor(StoreSettingsField.PHONE) != null,
                            supportingText = state.errorFor(StoreSettingsField.PHONE)?.let { message ->
                                { Text(message) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(spacing.md))
                        OutlinedTextField(
                            value = state.form.whatsapp,
                            onValueChange = onWhatsappChange,
                            singleLine = true,
                            label = { Text("WhatsApp") },
                            isError = state.errorFor(StoreSettingsField.WHATSAPP) != null,
                            supportingText = state.errorFor(StoreSettingsField.WHATSAPP)?.let { message ->
                                { Text(message) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SectionCard(title = "Opening") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.form.isOpen) "Accepting orders" else "Closed",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "Customers can only order while the store is open.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(spacing.md))
                            Switch(checked = state.form.isOpen, onCheckedChange = onOpenChange)
                        }
                    }

                    SectionCard(title = "Delivery & order limits") {
                        MoneyField(
                            value = state.form.deliveryFee,
                            onValueChange = onDeliveryFeeChange,
                            label = "Delivery fee",
                            error = state.errorFor(StoreSettingsField.DELIVERY_FEE),
                        )
                        Spacer(Modifier.size(spacing.md))
                        MoneyField(
                            value = state.form.freeDeliveryThreshold,
                            onValueChange = onFreeDeliveryThresholdChange,
                            label = "Free delivery over",
                            error = state.errorFor(StoreSettingsField.FREE_DELIVERY_THRESHOLD),
                        )
                        Spacer(Modifier.size(spacing.md))
                        MoneyField(
                            value = state.form.minOrderAmount,
                            onValueChange = onMinOrderAmountChange,
                            label = "Minimum order",
                            error = state.errorFor(StoreSettingsField.MIN_ORDER_AMOUNT),
                        )
                    }

                    state.saveError?.let { error ->
                        Text(
                            text = ErrorCopy.of(error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                SaveBar(
                    enabled = state.isDirty && !state.isSaving && online,
                    isSaving = state.isSaving,
                    offline = !online,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun StoreNameWarning(storeName: String) {
    val spacing = VedantTheme.spacing
    Surface(
        color = WarningBg,
        contentColor = Warning,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(VedantTheme.sizing.iconMd),
            )
            Column {
                Text(
                    text = "This name looks wrong",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(spacing.xxs))
                Text(
                    text = "The store is saved as \"$storeName\", which is not this store's name. " +
                        "It is shown as-is so you can correct it — nothing is changed until you save.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        prefix = { Text("₹") },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SaveBar(enabled: Boolean, isSaving: Boolean, offline: Boolean, onSave: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = VedantTheme.sizing.divider,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(modifier = Modifier.padding(spacing.screen)) {
                if (offline) {
                    Text(
                        text = "Reconnect to save your changes — nothing is queued while offline.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = spacing.sm),
                    )
                }
                PrimaryButton(
                    text = "Save settings",
                    onClick = onSave,
                    enabled = enabled,
                    isInFlight = isSaving,
                    leadingIcon = Icons.Filled.Save,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
