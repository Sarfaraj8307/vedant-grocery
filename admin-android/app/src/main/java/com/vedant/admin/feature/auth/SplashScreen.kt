package com.vedant.admin.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S1 — Splash / session check.
 *
 * Shows the brand and a spinner while the session is restored. On a showable
 * failure it offers a retry rather than silently dumping the user on sign-in.
 */
@Composable
fun SplashScreen(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val viewModel: SplashViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SplashContent(state = state, onRetry = viewModel::restore, modifier = modifier)
}

@Composable
private fun SplashContent(
    state: SplashUiState,
    onRetry: () -> Unit,
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
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.emptyIcon),
            )
            Spacer(Modifier.size(spacing.md))
            Text(
                text = "Vedant Admin",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Store operations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(spacing.xxl))

            if (state.error != null) {
                Text(
                    text = ErrorCopy.of(state.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(spacing.md))
                SecondaryButton(text = "Try again", onClick = onRetry)
            } else if (state.isRestoring) {
                CircularProgressIndicator(strokeWidth = sizing.progressStroke)
            }
        }
    }
}
