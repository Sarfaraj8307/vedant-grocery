package com.vedant.admin.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.theme.Berry500
import com.vedant.admin.ui.theme.Marigold600
import com.vedant.admin.ui.theme.Marigold700
import com.vedant.admin.ui.theme.SurfaceWhite
import com.vedant.admin.ui.theme.Leaf100
import com.vedant.admin.ui.theme.Leaf500
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Leaf700
import com.vedant.admin.ui.theme.Leaf800
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.coroutines.delay

/**
 * One destination on the dashboard grid.
 *
 * Data rather than six hand-written button calls: the grid then renders uniformly,
 * the stagger animation can key off the index, and adding a destination is one
 * entry instead of another copy of the same layout.
 */
private data class DashboardTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val accent: Color,
)

/**
 * Dashboard — the operational home.
 *
 * Layout notes (reference: JetSnack, `android/compose-samples`):
 *  • A **gradient hero** anchors the screen instead of a plain top bar, so the
 *    operator sees who is signed in and what state the store is in immediately.
 *  • Destinations are **grid tiles**, two per row, with the icon in a tinted
 *    circle — scannable at a glance and reachable with one thumb.
 *  • Tiles **reveal on a stagger** (fade + rise) when the screen opens. The motion
 *    is driven by an [Animatable] per tile rather than `animateFloatAsState`, so
 *    each card settles once instead of re-animating on every recomposition.
 *  • `LocalReduceMotion` short-circuits the animation entirely — the tiles are
 *    simply there. Motion is decoration, never information.
 *
 * Order acceptance stays a full-width card above the grid because it is the one
 * control the owner reaches for in a hurry.
 */
@Composable
fun DashboardScreen(
    factory: ViewModelProvider.Factory,
    email: String?,
    onOpenOrders: () -> Unit,
    onOpenDelivery: () -> Unit,
    onOpenCatalogue: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenStoreSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val reduceMotion = LocalReduceMotion.current

    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val dash by viewModel.uiState.collectAsStateWithLifecycle()
    var stopReason by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.start() }

    val tiles = listOf(
        DashboardTile(
            title = "Orders",
            subtitle = "Review and update",
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            onClick = onOpenOrders,
            accent = Leaf600,
        ),
        DashboardTile(
            title = "Deliveries",
            subtitle = "Today's runs",
            icon = Icons.Outlined.LocalShipping,
            onClick = onOpenDelivery,
            accent = Leaf500,
        ),
        DashboardTile(
            title = "Catalogue",
            subtitle = "Products & offers",
            icon = Icons.Filled.Inventory2,
            onClick = onOpenCatalogue,
            accent = Marigold600,
        ),
        DashboardTile(
            title = "Slots",
            subtitle = "Capacity planning",
            icon = Icons.Filled.DateRange,
            onClick = onOpenSlots,
            accent = Marigold700,
        ),
        DashboardTile(
            title = "Support",
            subtitle = "Customer issues",
            icon = Icons.Filled.SupportAgent,
            onClick = onOpenSupport,
            accent = Berry500,
        ),
        DashboardTile(
            title = "Reporting",
            subtitle = "Today's numbers",
            icon = Icons.Filled.Analytics,
            onClick = onOpenAnalytics,
            accent = Leaf500,
        ),
        DashboardTile(
            title = "Store settings",
            subtitle = "Fees & profile",
            icon = Icons.Filled.Storefront,
            onClick = onOpenStoreSettings,
            accent = Leaf700,
        ),
    )

    Column(modifier = modifier.fillMaxSize()) {
        // --- Hero ------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Leaf700, Leaf800)),
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.padding(
                    start = spacing.screen,
                    end = spacing.xs,
                    top = spacing.lg,
                    bottom = spacing.xl,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vedant",
                            style = MaterialTheme.typography.labelLarge,
                            color = Leaf100,
                        )
                        Text(
                            text = "Store operations",
                            style = MaterialTheme.typography.headlineSmall,
                            color = SurfaceWhite,
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = Leaf100,
                        )
                    }
                }
                Text(
                    text = email ?: "Administrator",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Leaf100,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.lg,
                bottom = spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            // --- Order acceptance (full width, above the grid) ----------------
            item(span = { GridItemSpan(maxLineSpan) }) {
                OrderAcceptanceCard(
                    accepting = dash.accepting,
                    isSaving = dash.isSaving,
                    message = dash.message,
                    messageIsError = dash.messageIsError,
                    reason = stopReason,
                    onReasonChange = { stopReason = it },
                    onStart = { viewModel.setAccepting(true, null) },
                    onStop = { viewModel.setAccepting(false, stopReason) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Manage today's orders, update their status and handle customer issues.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }

            itemsIndexed(tiles) { index, tile ->
                RevealOnMount(index = index, enabled = !reduceMotion) {
                    DashboardGridTile(tile)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    onClick = onOpenProfile,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(VedantTheme.radius.xl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(spacing.lg),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your profile",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Appearance, account and sign out",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fade + rise a child once, staggered by its position in the grid.
 *
 * Skipped entirely when [enabled] is false (reduce motion), in which case the
 * child renders at its final position with no animation frame at all.
 */
@Composable
private fun RevealOnMount(
    index: Int,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val progress = remember { Animatable(0f) }
    // Resolved here, at composable scope: `VedantTheme.spacing` is a composable
    // getter, so it cannot be read inside `graphicsLayer`'s draw scope.
    val rise = with(LocalDensity.current) { VedantTheme.spacing.xxl.toPx() }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 320))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * rise
        },
    ) {
        content()
    }
}

/** One destination: tinted icon, title, subtitle, elevated card. */
@Composable
private fun DashboardGridTile(tile: DashboardTile) {
    val spacing = VedantTheme.spacing
    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(VedantTheme.sizing.avatar)
                    .background(tile.accent.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = tile.accent,
                    modifier = Modifier.size(VedantTheme.sizing.iconMd),
                )
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The one-click stop/resume control, kept full-width and above the grid. */
@Composable
private fun OrderAcceptanceCard(
    accepting: Boolean?,
    isSaving: Boolean,
    message: String?,
    messageIsError: Boolean,
    reason: String,
    onReasonChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Text(
                text = "Order acceptance",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(VedantTheme.spacing.sm)
                        .background(
                            when (accepting) {
                                true -> Leaf500
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.outline
                            },
                            CircleShape,
                        ),
                )
                Spacer(Modifier.size(spacing.sm))
                Text(
                    text = when (accepting) {
                        true -> "Accepting orders"
                        false -> "Not accepting orders"
                        null -> "Status unknown"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = "Closing stops NEW orders immediately. Existing orders, " +
                    "delivery and support keep working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (accepting == false) {
                PrimaryButton(
                    text = "Start taking orders",
                    onClick = onStart,
                    isInFlight = isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Stop taking orders",
                    onClick = onStop,
                    isInFlight = isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Leaf600
                    },
                )
            }
        }
    }
}
