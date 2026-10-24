package com.vedant.admin.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vedant.admin.core.network.ConnectionState
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme
import com.vedant.admin.ui.theme.Warning
import com.vedant.admin.ui.theme.WarningBg

/**
 * Which of the four explicit content states a screen is in (PRD §5.2). There is
 * no implicit "null means empty" — every data screen states its phase.
 */
enum class ContentPhase { Loading, Data, Empty, Error }

/**
 * The standard frame every data screen renders through.
 *
 * It guarantees three things the PRD requires and that are easy to forget
 * screen-by-screen:
 *  - **Loading / Empty / Error are first-class**, with copy and an optional retry.
 *  - **The offline banner is persistent**, not a toast that disappears. It is
 *    driven by [LocalOnlineStatus] by default, so every screen shows it without
 *    threading the flag through.
 *  - **State transitions are animated** (`AnimatedContent`) without ever gating
 *    an operational action — motion here is purely cosmetic.
 *
 * When [phase] is [ContentPhase.Data] the caller's [content] is shown.
 *
 * @param loadingContent optional **skeleton** for the loading phase — a screen
 *   whose shape is knowable (a list of cards) should show its own skeleton so
 *   the layout does not jump when data lands. When omitted, the default
 *   spinner + "Loading…" still applies (right for genuinely shapeless loads).
 */
@Composable
fun StateScaffold(
    phase: ContentPhase,
    modifier: Modifier = Modifier,
    isOffline: Boolean = !LocalOnlineStatus.current,
    emptyIcon: ImageVector = Icons.Outlined.Inbox,
    emptyTitle: String = "Nothing here yet",
    emptyMessage: String? = null,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        ConnectionBanner(fallbackOffline = isOffline)

        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "content-phase",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                ContentPhase.Loading -> loadingContent?.invoke() ?: LoadingState()
                ContentPhase.Empty ->
                    CenteredMessage(
                        icon = emptyIcon,
                        title = emptyTitle,
                        message = emptyMessage,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                ContentPhase.Error ->
                    ErrorState(message = errorMessage, onRetry = onRetry)
                ContentPhase.Data -> content()
            }
        }
    }
}

/**
 * The banner for whichever connection state the app is in (§52).
 *
 * The wording is the whole point: the old code had one string for every failure,
 * so a dead backend told the operator to check a connection that was fine. Now:
 *
 *  - [ConnectionState.Offline]  — "showing saved data" is truthful: the rows on
 *    screen really are from the cache.
 *  - [ConnectionState.Error]    — the network is present but the store could not
 *    be reached. Says so, and names the server reason. **No** "check your
 *    connection", because there is nothing wrong with it.
 *  - [ConnectionState.Syncing]  — quiet, non-blocking: a read is in flight.
 *  - [ConnectionState.Synced] / [ConnectionState.Online] — nothing at all.
 */
@Composable
fun ConnectionBanner(
    fallbackOffline: Boolean = !LocalOnlineStatus.current,
    modifier: Modifier = Modifier,
) {
    val monitor = LocalConnectionMonitor.current
    val state: ConnectionState = when {
        monitor != null -> monitor.state.collectAsStateWithLifecycle().value
        fallbackOffline -> ConnectionState.Offline
        else -> ConnectionState.Online
    }

    val reduceMotion = LocalReduceMotion.current
    val copy: String = when (state) {
        ConnectionState.Offline -> "Offline — showing saved data"
        is ConnectionState.Error -> state.message
        ConnectionState.Syncing -> "Syncing…"
        // Nothing to say: the app is healthy.
        ConnectionState.Synced, ConnectionState.Online -> return
    }

    AnimatedVisibility(
        visible = true,
        enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        BannerSurface(copy = copy, isError = state is ConnectionState.Error)
    }
}

@Composable
private fun BannerSurface(copy: String, isError: Boolean, modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = WarningBg,
        contentColor = Warning,
        // Announced politely: a screen-reader user learns the app lost the
        // store without the banner stealing focus.
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(sizing.iconMd),
            )
            Text(text = copy, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The persistent "you are offline" strip. */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = if (LocalReduceMotion.current) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (LocalReduceMotion.current) ExitTransition.None else shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        BannerSurface(copy = "Offline — showing saved data", isError = false)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CircularProgressIndicator(
                strokeWidth = sizing.progressStroke,
                modifier = Modifier.size(sizing.iconLg),
            )
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    CenteredMessage(
        icon = Icons.Filled.ErrorOutline,
        title = message ?: "Something went wrong",
        message = null,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
        footer = {
            if (onRetry != null) {
                SecondaryButton(
                    text = "Try again",
                    onClick = onRetry,
                    leadingIcon = Icons.Filled.Refresh,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        },
    )
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    message: String?,
    iconTint: Color,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl),
        ) {
            // The glyph sits in a large tinted disc (HyperUI's empty-state
            // treatment). A bare 48 dp icon in the middle of a phone screen
            // reads as a stray artifact; the disc gives the state a centre of
            // gravity and makes an empty list look deliberate rather than broken.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(sizing.emptyBadge)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.10f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(sizing.emptyIcon),
                )
            }
            Spacer(Modifier.size(spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            footer?.invoke()
        }
    }
}
