package com.vedant.admin.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.money.Money
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Leaf400
import com.vedant.admin.ui.theme.Leaf500
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Marigold300
import com.vedant.admin.ui.theme.Marigold500
import com.vedant.admin.ui.theme.VedantTheme

/**
 * Reporting — the store's numbers, over a window the owner chooses (brief §28).
 *
 * Everything is server-computed: `admin_dashboard_counts()`, `admin_sales_range()`
 * and `admin_order_history()` return the figures, and this screen only lays them
 * out. The app performs no arithmetic on money except summing the per-day rows
 * the server already produced, so a number here cannot disagree with the database.
 *
 * "Full transparency" is structural, not a slogan: every total on the screen can
 * be traced to a specific day in **Daily breakdown**, and every day to the
 * individual orders in **Orders** — same server, same window, no hidden steps.
 *
 * Layout: range chips, four headline stat tiles, the live order pipeline, the
 * sales breakdown, the per-day table, then the order list. Bars are drawn rather
 * than charted — a charting dependency for a handful of rows would be dead
 * weight, and a plain bar is exactly as accurate.
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
            subtitle = state.range.caption,
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

        RangeSelector(
            selected = state.range,
            onSelect = viewModel::selectRange,
        )

        StateScaffold(
            phase = state.phase,
            emptyTitle = "No activity yet",
            emptyMessage = "Once orders start coming in, the numbers appear here.",
            errorMessage = "We could not load the numbers. Retry to try again.",
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
                        text = "Headline · ${state.range.caption}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                itemsIndexed(
                    listOf(
                        Triple("Orders", snap.orders.toString(), Leaf600),
                        Triple("Sales", snap.net.format(), Leaf500),
                        Triple("Gross", snap.gross.format(), Leaf500),
                        Triple("Avg order", snap.aov.format(), Marigold500),
                    ),
                ) { _, (label, value, accent) ->
                    StatTile(label = label, value = value, accent = accent)
                }

                // --- Live pipeline ------------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Order pipeline · right now",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    PipelineCard(counts = snap.pipeline)
                }

                // --- Money --------------------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Sales breakdown · ${state.range.caption}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    BreakdownCard(snapshot = snap)
                }

                // --- Per-day transparency ------------------------------------
                if (snap.dailyTotals.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "Daily breakdown",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DailyBreakdownCard(days = snap.dailyTotals)
                    }
                }

                // --- Individual orders --------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Orders · ${snap.history.size} shown",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (snap.history.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHistoryCard()
                    }
                } else {
                    itemsIndexed(snap.history, key = { _, row -> row.orderId }) { _, row ->
                        OrderHistoryCard(row = row)
                    }
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

/** The window picker. Chips rather than a date dialog: three useful spans, one tap. */
@Composable
private fun RangeSelector(
    selected: ReportRange,
    onSelect: (ReportRange) -> Unit,
) {
    val spacing = VedantTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
    ) {
        ReportRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
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

/** Money rows for gross -> net, over the selected window. */
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun MoneyRow(label: String, value: Money) {
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

/**
 * One row per day in the window, including days with no orders.
 *
 * This is the audit trail for the totals above: every rupee in "Net" appears on
 * exactly one line here. A quiet day shows `—` rather than being omitted, so a
 * missing line always means a broken query, never a slow Tuesday.
 */
@Composable
private fun DailyBreakdownCard(days: List<DayTotal>) {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = VedantTheme.sizing.cardShadow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Day",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Orders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(spacing.lg))
                Text(
                    text = "Net",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            days.forEach { day ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xs),
                ) {
                    Text(
                        text = day.date.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = day.orders.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(spacing.lg))
                    Text(
                        text = day.net.format(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * One order, with every figure the server holds.
 *
 * Full transparency means the adjustment (rejected-item) amount is shown, not
 * folded silently into the total: if a customer's bill differs from the list
 * value, the reason is on this card.
 */
@Composable
private fun OrderHistoryCard(row: OrderHistoryRow) {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.customerName.ifBlank { "Guest" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "#${row.orderId.take(8)} · ${row.itemCount} item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = row.finalTotal.format(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = buildString {
                    append(row.status.ifBlank { "UNKNOWN" })
                    row.placedAt?.let { append(" · ").append(it.take(16).replace('T', ' ')) }
                    if (!row.adjustmentAmount.isZero) {
                        append(" · adj ").append(row.adjustmentAmount.format())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    val spacing = VedantTheme.spacing
    Surface(
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "No orders in this window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(spacing.lg),
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
            Text(text = "Complaints · right now", style = MaterialTheme.typography.titleSmall)
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
