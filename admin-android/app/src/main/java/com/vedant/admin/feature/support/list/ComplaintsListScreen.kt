package com.vedant.admin.feature.support.list

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.ui.components.ComplaintStatusBadge
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.OrderListSkeleton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S7 — Complaints list.
 *
 * A scannable queue of customer complaints: the status facet and search stay
 * pinned above the list, pages load as the operator scrolls, skeleton rows fill
 * the load (not a bare spinner), and the empty/error phases are stated.
 *
 * A complaint's meaning is carried by **word + icon + colour** (the badge), never
 * by colour alone. An unknown wire status renders its raw value with a neutral
 * badge rather than being dropped.
 */
@Composable
fun ComplaintsListScreen(
    factory: ViewModelProvider.Factory,
    onOpenComplaint: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ComplaintsListViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    ComplaintsListContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onStatusSelected = viewModel::onStatusSelected,
        onSearchChange = viewModel::onSearchChange,
        onOpenComplaint = onOpenComplaint,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplaintsListContent(
    state: ComplaintsListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onStatusSelected: (ComplaintStatus?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenComplaint: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Complaints",
            subtitle = subtitleFor(state),
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh complaints")
                }
            },
        )

        StatusFilterRow(selected = state.filter.status, onSelect = onStatusSelected)

        OutlinedTextField(
            value = state.filter.search,
            onValueChange = onSearchChange,
            singleLine = true,
            label = { Text("Search complaints") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.filter.search.isNotEmpty()) {
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
                    emptyIcon = Icons.Filled.SupportAgent,
                    emptyTitle = if (state.isEmptyBecauseFiltered) "No matching complaints" else "No complaints yet",
                    emptyMessage = if (state.isEmptyBecauseFiltered) {
                        "Try a different status, or clear the search."
                    } else {
                        "Customer complaints appear here when they are raised."
                    },
                    errorMessage = state.error?.let(ErrorCopy::of),
                    onRetry = onRetry,
                ) {
                    ComplaintsList(
                        state = state,
                        listState = listState,
                        onOpenComplaint = onOpenComplaint,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplaintsList(
    state: ComplaintsListUiState,
    listState: LazyListState,
    onOpenComplaint: (String) -> Unit,
    onLoadMore: () -> Unit,
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
        if (refreshError != null && state.complaints.isNotEmpty()) {
            item(key = "refresh-error") {
                InlineError(message = ErrorCopy.of(refreshError), onRetry = onLoadMore)
            }
        }

        items(items = state.complaints, key = { it.id }) { complaint ->
            ComplaintRow(complaint = complaint, onClick = { onOpenComplaint(complaint.id) })
        }

        if (state.canLoadMore || state.loadMoreError != null) {
            item(key = "load-more") {
                LoadMoreFooter(
                    isLoading = state.isLoadingMore,
                    error = state.loadMoreError,
                    onRetry = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun ComplaintRow(complaint: Complaint, onClick: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = complaint.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(spacing.sm))
                Text(
                    text = AppClock.formatDateTime(complaint.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(spacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ComplaintStatusBadge(status = complaint.status, rawStatus = complaint.statusRaw)
                Spacer(Modifier.size(spacing.sm))
                Text(
                    text = complaint.typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                complaint.orderId?.takeIf { it.isNotBlank() }?.let { orderId ->
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = "Order #${orderId.takeLast(8).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            complaint.customerName?.takeIf { it.isNotBlank() }?.let { name ->
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusFilterRow(selected: ComplaintStatus?, onSelect: (ComplaintStatus?) -> Unit) {
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
        ComplaintStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(status.label) },
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

private fun subtitleFor(state: ComplaintsListUiState): String? = when {
    state.phase == ContentPhase.Data ->
        if (state.canLoadMore) "${state.complaints.size} shown" else "${state.complaints.size} complaints"

    state.isRefreshing -> "Refreshing…"
    else -> null
}
