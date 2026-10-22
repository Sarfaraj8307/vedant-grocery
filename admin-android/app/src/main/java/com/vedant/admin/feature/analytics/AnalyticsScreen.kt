package com.vedant.admin.feature.analytics

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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Leaf400
import com.vedant.admin.ui.theme.Leaf500
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Marigold300
import com.vedant.admin.ui.theme.Marigold500
import com.vedant.admin.ui.theme.VedantTheme

/**
 * Reporting — today's operational numbers (brief §28).
 *
 * Everything is server-computed: `admin_dashboard_counts()` and
 * `admin_sales_today()` return the figures, and this screen only lays them out.
 * The app performs no arithmetic on money, so a number here cannot disagree with
 * what the database holds.
 *
 * Layout: four headline stat tiles, then the order pipeline as bars so the owner
 * sees where orders are stuck at a glance, then the sales breakdown, then
 * complaints. Bars are drawn rather than charted — a charting dependency for four
 * rows would be dead weight, and a plain bar is exactly as accurate.
 */
@Composable
fun AnalyticsScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val viewModel: AnalyticsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Reporting",
            subtitle = "Today",
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                    )
                }
            },
        )

        StateScaffold(
            phase = state.phase,
            emptyTitle = "No activity yet",
            emptyMessage = "Once orders start coming in, today's numbers appear here.",
            errorMessage = "We could not load today's numbers. Pull to retry.",
            onRetry = viewModel::refresh,
        ) {
            val snap = state.snapshot
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = spacing.screen,
                    end = spacing.screen,
                    top = spacing.md,
                    bottom = spacing.xxl,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Headline",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                itemsIndexed(
                    listOf(
                        Triple("Orders today", snap.todaysOrders.toString(), Leaf600),
                        Triple("Sales today", snap.todaysSales.format(), Leaf500),
                        Triple("Net", snap.net.format(), Leaf600),
                        Triple("Avg order", snap.aov.format(), Marigold500),
                    ),
                ) { _, (label, value, accent) ->
                    StatTile(label = label, value = value, accent = accent)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Order pipeline",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    PipelineCard(counts = snap.pipeline)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Sales breakdown",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    BreakdownCard(snapshot = snap)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ComplaintsCard(
                        pending = snap.pendingComplaints,
                        open = snap.openComplaints,
                    )
                }
            }
        }
    }
}

/** One headline number. */
@Composable
private fun StatTile(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(VedantTheme.spacing.sm)
                    .background(accent, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/**
 * The order pipeline as bars.
 *
 * The bar width is relative to the largest bucket, so the shape is readable at a
 * glance and no bucket is ever scaled against a hard-coded maximum that could
 * clip a busy day.
 */
@Composable
private fun PipelineCard(counts: List<PipelineCount>) {
    val spacing = VedantTheme.spacing
    val peak = counts.maxOfOrNull { it.count } ?: 0
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
            counts.forEach { item ->
                val fraction = if (peak <= 0) 0f else item.count.toFloat() / peak.toFloat()
                Column(
                    modifier = Modifier.semantics {
                        contentDescription = "${item.label}: ${item.count}"
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = item.count.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(spacing.xxs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VedantTheme.sizing.progressStroke)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(VedantTheme.radius.xs),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(VedantTheme.sizing.progressStroke)
                                .background(
                                    item.accentKey.colour(),
                                    RoundedCornerShape(VedantTheme.radius.xs),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Money rows for gross → net. */
@Composable
private fun BreakdownCard(snapshot: AnalyticsSnapshot) {
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
            MoneyRow("Gross", snapshot.gross)
            MoneyRow("Discounts", snapshot.discounts)
            MoneyRow("Adjustments", snapshot.adjustments)
            MoneyRow("Refunds", snapshot.refunds)
            MoneyRow("Delivery", snapshot.deliveryRevenue)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Net",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = snapshot.net.format(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: com.vedant.admin.core.money.Money) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.format(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ComplaintsCard(pending: Int, open: Int) {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.padding(spacing.lg),
        ) {
            Text(text = "Complaints", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "$pending awaiting a first reply · $open still open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Accent colour for a pipeline bucket. Composable because `MaterialTheme` is. */
@Composable
private fun PipelineAccent.colour(): androidx.compose.ui.graphics.Color = when (this) {
    PipelineAccent.NEUTRAL -> Leaf400
    PipelineAccent.ATTENTION -> Marigold300
    PipelineAccent.POSITIVE -> Leaf500
    PipelineAccent.NEGATIVE -> MaterialTheme.colorScheme.error
}
