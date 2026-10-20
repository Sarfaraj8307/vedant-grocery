package com.vedant.admin.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S3 — Not authorized.
 *
 * Shown when a signed-in account is not a store administrator. It reveals **no
 * admin data** and offers only sign-out — the screen is a dead end by design, so
 * `Back` exits rather than reaching an admin surface.
 */
@Composable
fun NotAuthorizedScreen(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotAuthorizedViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NotAuthorizedContent(
        isSigningOut = state.isSigningOut,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
private fun NotAuthorizedContent(
    isSigningOut: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.xl),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.emptyIcon),
            )
            Spacer(Modifier.size(spacing.lg))
            Text(
                text = "Not authorized",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = "This account isn't a store administrator. Sign in with an " +
                    "administrator account, or contact the store owner.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(spacing.xl))
            SecondaryButton(
                text = "Sign out",
                onClick = onSignOut,
                isInFlight = isSigningOut,
                leadingIcon = Icons.AutoMirrored.Filled.Logout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
