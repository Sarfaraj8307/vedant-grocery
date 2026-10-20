package com.vedant.admin.feature.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S8b — Appearance.
 *
 * The persisted System/Light/Dark choice (§8). A radio group, not a switch:
 * "dark mode" is a three-way decision, and the system option has to stay
 * reachable once an operator has pinned one of the other two.
 *
 * The choice is written straight to storage and read back by `MainActivity`,
 * so the change applies immediately and survives a restart. The note under
 * the options says exactly that, so nobody wonders whether the app will
 * remember it.
 */
@Composable
fun AppearanceScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AppearanceViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Appearance",
            subtitle = state.mode.label,
            onBack = onBack,
        )

        AppearanceOptions(
            selected = state.mode,
            onSelect = viewModel::select,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The three choices as a single-select list, one row per option. */
@Composable
private fun AppearanceOptions(
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
    ) {
        SectionCard(title = "Theme") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AppearanceMode.entries.forEach { mode ->
                    AppearanceOptionRow(
                        mode = mode,
                        selected = mode == selected,
                        onSelect = { onSelect(mode) },
                    )
                }
            }
            Text(
                text = "Your choice is saved on this device and applied the next " +
                    "time the app opens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
    }
}

/** One radio row: icon, name, and what it means for this device. */
@Composable
private fun AppearanceOptionRow(
    mode: AppearanceMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = spacing.sm),
    ) {
        Icon(
            imageVector = mode.icon(),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.md),
        ) {
            Text(text = mode.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = mode.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** The icon per option — decorative, so the copy carries the meaning. */
private fun AppearanceMode.icon(): ImageVector = when (this) {
    AppearanceMode.SYSTEM_DEFAULT -> Icons.Filled.SettingsBrightness
    AppearanceMode.LIGHT -> Icons.Filled.LightMode
    AppearanceMode.DARK -> Icons.Filled.DarkMode
}

/** One line of "what this does" per option, so the choice needs no tooltip. */
private fun AppearanceMode.description(): String = when (this) {
    AppearanceMode.SYSTEM_DEFAULT -> "Matches your phone's dark-mode setting"
    AppearanceMode.LIGHT -> "Always the light scheme"
    AppearanceMode.DARK -> "Always the dark scheme"
}
