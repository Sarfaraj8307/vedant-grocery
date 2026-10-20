package com.vedant.admin.feature.settings.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.domain.model.OperatorProfile
import com.vedant.admin.ui.components.BadgeTone
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.DestructiveButton
import com.vedant.admin.ui.components.DetailRow
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.StatusBadge
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S13 — Profile.
 *
 * The signed-in identity and — the point of the screen — the **server-resolved
 * role**. The role badge is built from the `is_admin()` answer, never from a
 * locally cached value; a note under it says exactly that, so the operator knows
 * the role is authoritative rather than remembered.
 *
 * When the session has expired the screen says so plainly and offers sign-in
 * again instead of rendering a stale identity. Sign-out clears the session; the
 * navigation gate then returns the app to sign-in.
 */
@Composable
fun ProfileScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAppearance: () -> Unit = {},
) {
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Profile",
            subtitle = state.profile?.displayName,
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::signOut, enabled = !state.isSigningOut) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                }
            },
        )

        StateScaffold(
            phase = state.phase,
            emptyTitle = "Not signed in",
            emptyMessage = "Sign in to see your profile.",
            errorMessage = state.loadError?.let(ErrorCopy::of),
            onRetry = viewModel::reload,
            modifier = Modifier.fillMaxSize(),
        ) {
            val profile = state.profile
            if (profile != null) {
                ProfileBody(
                    profile = profile,
                    isSigningOut = state.isSigningOut,
                    signOutError = state.signOutError?.let(ErrorCopy::of),
                    appearanceMode = state.appearanceMode,
                    onSignOut = viewModel::signOut,
                    onReload = viewModel::reload,
                    onOpenAppearance = onOpenAppearance,
                )
            }
        }
    }
}

@Composable
private fun ProfileBody(
    profile: OperatorProfile,
    isSigningOut: Boolean,
    signOutError: String?,
    appearanceMode: AppearanceMode,
    onSignOut: () -> Unit,
    onReload: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val spacing = VedantTheme.spacing

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.screen),
    ) {
        IdentityHeader(profile = profile)

        SectionCard(title = "Device") {
            AppearanceEntry(
                current = appearanceMode.label,
                onClick = onOpenAppearance,
            )
        }

        SectionCard(title = "Access") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    label = profile.roleLabel,
                    tone = if (profile.isAdmin) BadgeTone.Success else BadgeTone.Danger,
                    icon = Icons.Filled.Info,
                )
            }
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = "This role is confirmed by the server (is_admin()) on every load — " +
                    "it is never read from a value stored on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Account") {
            DetailRow(label = "Name", value = profile.displayName)
            profile.username?.takeIf { it.isNotBlank() }?.let { DetailRow(label = "Username", value = it) }
            DetailRow(label = "Email", value = profile.email ?: "—")
            profile.phone?.takeIf { it.isNotBlank() }?.let { DetailRow(label = "Phone", value = it) }
            profile.storedRole?.takeIf { it.isNotBlank() }?.let { stored ->
                DetailRow(label = "Profile record role", value = stored)
            }
            Text(
                text = "The profile record's role is informational only; it is not used for access.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        signOutError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        DestructiveButton(
            text = "Sign out",
            onClick = onSignOut,
            isInFlight = isSigningOut,
            leadingIcon = Icons.AutoMirrored.Filled.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The settings entry that opens the appearance screen (§8).
 *
 * The current choice is the row's value — not just a static label — so the
 * entry is useful even before it is tapped.
 */
@Composable
private fun AppearanceEntry(
    current: String,
    onClick: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.SettingsBrightness,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.iconMd),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.md),
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IdentityHeader(profile: OperatorProfile) {    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(sizing.avatar)
                    .clip(CircleShape),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = profile.displayName.trim().take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.email ?: profile.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
