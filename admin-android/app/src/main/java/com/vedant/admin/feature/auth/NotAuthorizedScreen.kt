package com.vedant.admin.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.theme.DURATION_SLOW
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.Leaf100
import com.vedant.admin.ui.theme.Leaf700
import com.vedant.admin.ui.theme.Leaf800
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.SurfaceWhite
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S3 — Not authorized.
 *
 * Shown when a signed-in account is not a store administrator. It reveals **no
 * admin data** and offers only sign-out — the screen is a dead end by design, so
 * `Back` exits rather than reaching an admin surface.
 *
 * Uses a dark gradient backdrop (matching the splash treatment) with a
 * centred lock icon in a tinted circle. The entrance animation scales and
 * fades in the icon; `LocalReduceMotion` freezes it at full alpha.
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
    val reduceMotion = LocalReduceMotion.current

    val iconScale = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            iconScale.animateTo(
                1f,
                tween(durationMillis = DURATION_SLOW, easing = EasingDecelerate),
            )
            kotlinx.coroutines.delay(100)
            textAlpha.animateTo(
                1f,
                tween(durationMillis = 300, easing = EasingDecelerate),
            )
        } else {
            iconScale.snapTo(1f)
            textAlpha.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Leaf700, Leaf800))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.xl),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(spacing.xxxl)
                    .clip(CircleShape)
                    .background(SurfaceWhite.copy(alpha = 0.12f))
                    .graphicsLayer {
                        val s = iconScale.value.coerceAtLeast(0.01f)
                        scaleX = s
                        scaleY = s
                        alpha = iconScale.value
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(sizing.emptyIcon),
                )
            }
            Spacer(Modifier.size(spacing.lg))
            Text(
                text = "Not authorized",
                style = MaterialTheme.typography.headlineSmall,
                color = SurfaceWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = "This account isn't a store administrator. Sign in with an " +
                    "administrator account, or contact the store owner.",
                style = MaterialTheme.typography.bodyMedium,
                color = Leaf100,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
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
