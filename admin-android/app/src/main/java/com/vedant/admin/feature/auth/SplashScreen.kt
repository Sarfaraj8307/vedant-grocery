package com.vedant.admin.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.theme.DURATION_SLOW
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.EasingStandard
import com.vedant.admin.ui.theme.Leaf100
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Leaf700
import com.vedant.admin.ui.theme.Leaf800
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.SurfaceWhite
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.coroutines.delay

/**
 * S1 — Splash / session check.
 *
 * Shows the brand and a spinner while the session is restored. On a showable
 * failure it offers a retry rather than silently dumping the user on sign-in.
 *
 * The splash uses a gradient backdrop with a brand-leaf hero that scales
 * and fades in on mount. The motion is cosmetic and respects
 * `LocalReduceMotion` — when reduced motion is active, the icon and text
 * appear at their final position with no animation frame.
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
    val reduceMotion = LocalReduceMotion.current

    // ── Entrance animations ───────────────────────────────────────────
    // A single Animatable drives a shared progress value; the icon scales
    // from 0.6 to 1.0 and fades from 0 to 1 simultaneously. The text
    // elements follow with a slight stagger. Each uses the same decelerate
    // curve so the motion feels unified.
    val iconScale = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            // Icon enters first: scale + fade
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DURATION_SLOW,
                    easing = EasingDecelerate,
                ),
            )
            // Text follows after a short stagger
            delay(100)
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = EasingStandard,
                ),
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
            // ── Brand icon in a tinted circle ──────────────────────────
            // The circle gives the icon visual weight on the gradient and
            // reads as a deliberate logo treatment rather than a raw glyph.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(spacing.xxxl)
                    .clip(CircleShape)
                    .background(Leaf600.copy(alpha = 0.25f))
                    .graphicsLayer {
                        scaleX = iconScale.value.coerceAtLeast(0.01f)
                        scaleY = iconScale.value.coerceAtLeast(0.01f)
                        alpha = iconScale.value
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(sizing.emptyIcon),
                )
            }
            Spacer(Modifier.size(spacing.md))
            Text(
                text = "Vedant Admin",
                style = MaterialTheme.typography.headlineMedium,
                color = SurfaceWhite,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Text(
                text = "Store operations",
                style = MaterialTheme.typography.bodyMedium,
                color = Leaf100,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )

            Spacer(Modifier.size(spacing.xxl))

            if (state.error != null) {
                Text(
                    text = ErrorCopy.of(state.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SurfaceWhite.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
                )
                Spacer(Modifier.size(spacing.md))
                SecondaryButton(text = "Try again", onClick = onRetry)
            } else if (state.isRestoring) {
                CircularProgressIndicator(
                    strokeWidth = sizing.progressStroke,
                    color = SurfaceWhite,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
                )
            }
        }
    }
}
