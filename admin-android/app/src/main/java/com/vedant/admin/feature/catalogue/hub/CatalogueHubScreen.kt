package com.vedant.admin.feature.catalogue.hub

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.domain.model.Product
import com.vedant.admin.feature.catalogue.ProductThumb
import com.vedant.admin.ui.components.BadgeTone
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.OfflineHint
import com.vedant.admin.ui.components.OrderListSkeleton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.StatusBadge
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S6 — Catalogue hub.
 *
 * A dense, scannable product list: thumbnail, name, category, price and an
 * availability affordance. Search and the category/availability facets are
 * pinned above the list and applied **server-side**; the availability toggle
 * writes through the repository and only moves the switch once the server has
 * confirmed the new row.
 */
@Composable
fun CatalogueHubScreen(
    factory: ViewModelProvider.Factory,
    onOpenProduct: (String?) -> Unit,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CatalogueHubViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }

    CatalogueHubContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onSearchChange = viewModel::onSearchChange,
        onCategorySelected = viewModel::onCategorySelected,
        onAvailabilitySelected = viewModel::onAvailabilitySelected,
        onToggleAvailability = viewModel::toggleAvailability,
        onDismissActionError = viewModel::dismissActionError,
        onOpenProduct = onOpenProduct,
        onAddProduct = { onOpenProduct(null) },
        onOpenCategories = onOpenCategories,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogueHubContent(
    state: CatalogueHubUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAvailabilitySelected: (Boolean?) -> Unit,
    onToggleAvailability: (Product) -> Unit,
    onDismissActionError: () -> Unit,
    onOpenProduct: (String?) -> Unit,
    onAddProduct: () -> Unit,
    onOpenCategories: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Catalogue",
            subtitle = subtitleFor(state),
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenCategories) {
                    Icon(Icons.Filled.Category, contentDescription = "Manage categories")
                }
                IconButton(onClick = onAddProduct) {
                    Icon(Icons.Filled.Add, contentDescription = "Add product")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh catalogue")
                }
            },
        )

        OutlinedTextField(
            value = state.query.search,
            onValueChange = onSearchChange,
            singleLine = true,
            label = { Text("Search products") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.search.isNotEmpty()) {
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

        CategoryFacetRow(state = state, onSelect = onCategorySelected)
        AvailabilityFacetRow(selected = state.query.availability, onSelect = onAvailabilitySelected)

        if (!online) {
            OfflineHint(
                text = "Availability can't be changed offline.",
                modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.xs),
            )
        }

        state.actionError?.let { error ->
            InlineError(message = ErrorCopy.of(error), onDismiss = onDismissActionError)
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.phase == ContentPhase.Loading) {
                OrderListSkeleton()
            } else {
                StateScaffold(
                    phase = state.phase,
                    emptyIcon = Icons.Filled.Inventory2,
                    emptyTitle = if (state.isEmptyBecauseFiltered) "No matching products" else "No products yet",
                    emptyMessage = if (state.isEmptyBecauseFiltered) {
                        "Try another category, or clear the search."
                    } else {
                        "Add your first product to start selling."
                    },
                    errorMessage = state.error?.let(ErrorCopy::of),
                    onRetry = onRetry,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.screen,
                            end = spacing.screen,
                            top = spacing.sm,
                            bottom = spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = state.products, key = { it.id }) { product ->
                            ProductRow(
                                product = product,
                                categoryName = state.categoryName(product.categoryId),
                                isToggling = state.togglingProductId == product.id,
                                onClick = { onOpenProduct(product.id) },
                                onToggleAvailability = { onToggleAvailability(product) },
                            )
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
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    categoryName: String?,
    isToggling: Boolean,
    onClick: () -> Unit,
    onToggleAvailability: () -> Unit,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    // A mutation that cannot reach the server is disabled, never queued (T24).
    val online = LocalOnlineStatus.current
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.lg),
        ) {
            ProductThumb(
                imageUrl = product.imageUrl,
                glyph = product.displayGlyph,
                size = sizing.listThumb,
            )

            Spacer(Modifier.size(spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(spacing.xxs))
                Text(
                    text = listOfNotNull(categoryName ?: "Uncategorised", product.unitSuffix)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = product.effectivePrice.format(), style = NumericEmphasis)
                    if (product.hasDiscount) {
                        Spacer(Modifier.size(spacing.sm))
                        Text(
                            text = product.basePrice.format(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Spacer(Modifier.size(spacing.sm))
                AvailabilityBadge(product = product)
            }

            Spacer(Modifier.size(spacing.md))

            if (isToggling) {
                CircularProgressIndicator(
                    strokeWidth = sizing.progressStroke,
                    modifier = Modifier.size(sizing.iconLg),
                )
            } else {
                Switch(
                    checked = product.availability,
                    onCheckedChange = { onToggleAvailability() },
                    enabled = online,
                )
            }
        }
    }
}

/** Availability is conveyed by **word + icon + colour** (WCAG 1.4.1), never colour alone. */
@Composable
private fun AvailabilityBadge(product: Product) {
    val (tone, icon) = when {
        !product.isActive -> BadgeTone.Neutral to Icons.Filled.VisibilityOff
        product.availability -> BadgeTone.Success to Icons.Filled.CheckCircle
        else -> BadgeTone.Danger to Icons.Filled.Inventory2
    }
    StatusBadge(label = product.availabilityLabel, tone = tone, icon = icon)
}

@Composable
private fun CategoryFacetRow(state: CatalogueHubUiState, onSelect: (String?) -> Unit) {
    val spacing = VedantTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen),
    ) {
        FilterChip(
            selected = state.query.categoryId == null,
            onClick = { onSelect(null) },
            label = { Text("All categories") },
        )
        state.categories.forEach { category ->
            FilterChip(
                selected = state.query.categoryId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text("${category.displayGlyph} ${category.name}") },
            )
        }
    }
}

@Composable
private fun AvailabilityFacetRow(selected: Boolean?, onSelect: (Boolean?) -> Unit) {
    val spacing = VedantTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.xs),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All stock") })
        FilterChip(selected = selected == true, onClick = { onSelect(true) }, label = { Text("Available") })
        FilterChip(selected = selected == false, onClick = { onSelect(false) }, label = { Text("Out of stock") })
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
private fun InlineError(message: String, onDismiss: () -> Unit) {
    val spacing = VedantTheme.spacing
    Surface(
        color = DangerBg,
        contentColor = Danger,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.md),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(spacing.sm))
            SecondaryButton(text = "Dismiss", onClick = onDismiss)
        }
    }
}

private fun subtitleFor(state: CatalogueHubUiState): String? = when {
    state.phase == ContentPhase.Data ->
        if (state.canLoadMore) "${state.products.size} shown" else "${state.products.size} products"
    state.isRefreshing -> "Refreshing…"
    else -> null
}
