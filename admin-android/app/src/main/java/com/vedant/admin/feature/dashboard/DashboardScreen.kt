package com.vedant.admin.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.PipelineStat
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.StatTile
import com.vedant.admin.ui.components.pressScale
import com.vedant.admin.ui.theme.Berry500
import com.vedant.admin.ui.theme.DURATION_STANDARD
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.Leaf100
import com.vedant.admin.ui.theme.Leaf500
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Leaf700
import com.vedant.admin.ui.theme.Leaf800
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.Marigold600
import com.vedant.admin.ui.theme.Marigold700
import com.vedant.admin.ui.theme.SurfaceWhite
import com.vedant.admin.ui.theme.VedantTheme
import com.vedant.admin.ui.theme.staggerDelay
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
 * **Order acceptance is a tile, not a banner.** It used to occupy a full-width
 * card above the grid, which handed a rarely-used control the most valuable
 * space on the screen. It now sits at the end of the grid — still one tap away,
 * but no longer competing with the destinations the operator opens every day.
 * Its tile subtitle carries the live open/closed state, so "are we taking
 * orders?" is still answerable without opening anything.
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
    onOpenOrderAcceptance: () -> Unit,
    onOpenStoreSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val reduceMotion = LocalReduceMotion.current

    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val dash by viewModel.uiState.collectAsStateWithLifecycle()
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
            subtitle = "Numbers & history",
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
        DashboardTile(
            title = "Order acceptance",
            subtitle = when (dash.accepting) {
                true -> "Accepting orders"
                false -> "Closed to new orders"
                null -> "Status unknown"
            },
            icon = Icons.Filled.PauseCircle,
            onClick = onOpenOrderAcceptance,
            accent = when (dash.accepting) {
                true -> Leaf600
                false -> Berry500
                null -> Marigold700
            },
        ),
    )

    Column(modifier = modifier.fillMaxSize()) {
        // --- Hero ------------------------------------------------------------
        // A three-stop gradient (Leaf600 -> Leaf700 -> Leaf800) gives the hero
        // more depth than a two-stop pair — the top reads brighter where the
        // brand name sits, and deepens toward the content edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Leaf600, Leaf700, Leaf800)),
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
            // --- Today at a glance ------------------------------------------
            // Two headline figures, then the pipeline. Placed above the
            // destinations because "how is today going?" is the question the
            // owner opens the app to answer, and it is answered without a tap.
            item(span = { GridItemSpan(maxLineSpan) }) {
                TodaySection(counts = dash.counts)
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
 * The day's numbers: two headline figures and the live pipeline.
 *
 * Shape follows HyperUI's `application/stats` cards — an icon in a tinted
 * circle, a quiet label, a heavy value — then a compact four-up pipeline row
 * underneath, which is the same idea as their stat strip but without four
 * nested cards on a phone-width screen.
 *
 * **Every unknown renders as an em dash, never as zero.** [counts] is null
 * until the first read lands and stays null if that read failed; painting "0"
 * there would tell the owner there is no work waiting when in fact nobody
 * asked the server. A zero the server actually reported still shows as "0",
 * because that is a fact.
 */
@Composable
private fun TodaySection(counts: DashboardCounts?) {
    val spacing = VedantTheme.spacing
    val unknown = "—"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            StatTile(
                label = "Today's orders",
                value = counts?.todaysOrders?.toString() ?: unknown,
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                accent = Leaf600,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Today's sales",
                value = counts?.todaysSales?.format() ?: unknown,
                icon = Icons.Filled.Payments,
                accent = Marigold600,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.size(spacing.md))

        SectionCard(title = "Right now") {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PipelineStat(
                    label = "New",
                    value = counts?.pending?.toString() ?: unknown,
                    // Pending is the only bucket that means "someone is
                    // waiting on us", so it alone takes the accent colour.
                    emphasis = (counts?.pending ?: 0) > 0,
                    modifier = Modifier.weight(1f),
                )
                PipelineStat(
                    label = "Packing",
                    value = counts?.active?.toString() ?: unknown,
                    modifier = Modifier.weight(1f),
                )
                PipelineStat(
                    label = "Ready",
                    value = counts?.ready?.toString() ?: unknown,
                    modifier = Modifier.weight(1f),
                )
                PipelineStat(
                    label = "Out",
                    value = counts?.outForDelivery?.toString() ?: unknown,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Fade + rise a child once, staggered by its position in the grid.
 *
 * Skipped entirely when [enabled] is false (reduce motion), in which case the
 * child renders at its final position with no animation frame at all.
 *
 * Uses the shared motion vocabulary from `Motion.kt`: 320 ms decelerate
 * tween, stagger capped at 350 ms so the last tile in a long list doesn't
 * wait seconds before it appears.
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
        delay(staggerDelay(index))
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = DURATION_STANDARD + 20, // 320 ms
                easing = EasingDecelerate,
            ),
        )
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

/** One destination: tinted icon, title, subtitle, elevated card with press feedback. */
@Composable
private fun DashboardGridTile(tile: DashboardTile) {
    val spacing = VedantTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource),
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
