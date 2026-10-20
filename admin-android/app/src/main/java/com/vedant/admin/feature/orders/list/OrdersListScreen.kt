package com.vedant.admin.feature.orders.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderSummary
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.OrderListSkeleton
import com.vedant.admin.ui.components.OrderStatusBadge
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S5 — Orders list.
 *
 * A dense, scannable operational list: the owner's default screen. It keeps the
 * filter and search pinned above the list, loads pages as the operator scrolls,
 * shows **skeleton rows** while loading (not a bare spinner), and states its
 * empty/error phases explicitly.
 */
@Composable
fun OrdersListScreen(
    factory: ViewModelProvider.Factory,
    initialStatus: OrderStatus?,
    onOpenOrder: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OrdersListViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start(initialStatus) }

    OrdersListContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onStatusSelected = viewModel::onStatusSelected,
        onSearchChange = viewModel::onSearchChange,
        onOpenOrder = onOpenOrder,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersListContent(
    state: OrdersListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onStatusSelected: (OrderStatus?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Orders",
            subtitle = subtitleFor(state),
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh orders")
                }
            },
        )

        FilterRow(selected = state.filter.status, onSelect = onStatusSelected)

        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onSearchChange,
            singleLine = true,
            label = { Text("Search by order reference") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
        )

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.phase == ContentPhase.Loading) {
                // Skeleton rows, not a spinner: the layout does not jump on load.
                OrderListSkeleton()
            } else {
                StateScaffold(
                    phase = state.phase,
                    emptyIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    emptyTitle = if (state.isEmptyBecauseFiltered) "No matching orders" else "No orders yet",
                    emptyMessage = if (state.isEmptyBecauseFiltered) {
                        "Try a different status, or clear the search."
                    } else {
                        "New orders appear here as customers place them."
                    },
                    errorMessage = state.error?.let(ErrorCopy::of),
                    onRetry = onRetry,
                ) {
                    OrdersList(
                        state = state,
                        listState = listState,
                        onOpenOrder = onOpenOrder,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdersList(
    state: OrdersListUiState,
    listState: LazyListState,
    onOpenOrder: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val spacing = VedantTheme.spacing

    // Prefetch the next page just before the operator reaches the end.
    val shouldLoadMore by remember(state.canLoadMore) {
        derivedStateOf {
            if (!state.canLoadMore || state.isLoadingMore) {
                false
            } else {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
            }
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = spacing.screen,
            end = spacing.screen,
            top = spacing.sm,
            bottom = spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        // A refresh that failed while rows are on screen: keep the rows, warn.
        val refreshError = state.error
        if (refreshError != null && state.orders.isNotEmpty()) {
            item(key = "refresh-error") { InlineError(message = ErrorCopy.of(refreshError), onRetry = onRetryLoadMore) }
        }

        items(items = state.orders, key = { it.id }) { order ->
            OrderRow(order = order, onClick = { onOpenOrder(order.id) })
        }

        if (state.canLoadMore || state.loadMoreError != null) {
            item(key = "load-more") {
                LoadMoreFooter(
                    isLoading = state.isLoadingMore,
                    error = state.loadMoreError,
                    onRetry = onRetryLoadMore,
                )
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderSummary, onClick: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(spacing.lg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${order.shortId}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = order.createdAt?.let(AppClock::formatDateTime) ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = order.customerLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrderStatusBadge(status = order.status, rawStatus = order.statusRaw)
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = if (order.itemCount == 1) "1 item" else "${order.itemCount} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(spacing.md))

            Column(horizontalAlignment = Alignment.End) {
                Text(text = order.finalTotal.format(), style = NumericEmphasis)
                Text(
                    text = "total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(selected: OrderStatus?, onSelect: (OrderStatus?) -> Unit) {
    val spacing = VedantTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        OrderStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(status.chipLabel()) },
            )
        }
    }
}

@Composable
private fun LoadMoreFooter(isLoading: Boolean, error: AppError?, onRetry: () -> Unit) {
    val spacing = VedantTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.lg),
    ) {
        when {
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = ErrorCopy.of(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(spacing.sm))
                SecondaryButton(text = "Try again", onClick = onRetry)
            }

            isLoading -> CircularProgressIndicator(
                strokeWidth = VedantTheme.sizing.progressStroke,
                modifier = Modifier.size(VedantTheme.sizing.iconLg),
            )
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        color = DangerBg,
        contentColor = Danger,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.md),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(spacing.sm))
            SecondaryButton(text = "Retry", onClick = onRetry)
        }
    }
}

/** Short chip wording so the filter row fits several statuses on one line. */
private fun OrderStatus.chipLabel(): String = when (this) {
    OrderStatus.PENDING -> "New"
    OrderStatus.CONFIRMED -> "Confirmed"
    OrderStatus.PROCESSING -> "Packing"
    OrderStatus.READY_FOR_DELIVERY -> "Ready"
    OrderStatus.OUT_FOR_DELIVERY -> "Out"
    OrderStatus.DELIVERED -> "Delivered"
    OrderStatus.CANCELLED -> "Cancelled"
}

private fun subtitleFor(state: OrdersListUiState): String? = when {
    state.phase == ContentPhase.Data -> if (state.canLoadMore) "${state.orders.size} shown" else "${state.orders.size} orders"
    state.isRefreshing -> "Refreshing…"
    else -> null
}
