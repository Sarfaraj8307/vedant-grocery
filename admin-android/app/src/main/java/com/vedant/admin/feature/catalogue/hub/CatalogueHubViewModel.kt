package com.vedant.admin.feature.catalogue.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.domain.repository.ProductRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the catalogue hub: paging, category/availability facets and debounced
 * search, plus the inline availability toggle.
 *
 * All filtering and paging happen **server-side** — filtering a loaded page
 * client-side would silently hide matches on other pages. The toggle returns the
 * **server-confirmed row**, which replaces the row in place; on failure the row
 * is left untouched and the error is surfaced.
 */
class CatalogueHubViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueHubUiState())
    val uiState: StateFlow<CatalogueHubUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadedPage = -1
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    /** Idempotent first load: categories (for the facet) and the first page. */
    fun start() {
        if (started) return
        started = true
        loadCategories()
        load(reset = true, mode = LoadMode.Skeleton)
    }

    fun onCategorySelected(categoryId: String?) {
        if (_uiState.value.query.categoryId == categoryId) return
        _uiState.update { it.copy(query = it.query.copy(categoryId = categoryId)) }
        load(reset = true, mode = LoadMode.Skeleton)
    }

    fun onAvailabilitySelected(availability: Boolean?) {
        if (_uiState.value.query.availability == availability) return
        _uiState.update { it.copy(query = it.query.copy(availability = availability)) }
        load(reset = true, mode = LoadMode.Skeleton)
    }

    /** Updates the field immediately, then reloads once typing pauses. */
    fun onSearchChange(query: String) {
        _uiState.update { it.copy(query = it.query.copy(search = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true, mode = LoadMode.Skeleton)
        }
    }

    fun refresh() = load(reset = true, mode = LoadMode.Refresh)

    fun retry() {
        if (_uiState.value.categories.isEmpty()) loadCategories()
        load(reset = true, mode = LoadMode.Skeleton)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingMore || state.phase != ContentPhase.Data) return
        load(reset = false, mode = LoadMode.Append)
    }

    /**
     * Flips a product's availability. The row is only replaced once the server
     * confirms the change; a rejection leaves the row and surfaces the error.
     */
    fun toggleAvailability(product: Product) {
        if (_uiState.value.togglingProductId != null) return
        _uiState.update { it.copy(togglingProductId = product.id, actionError = null) }
        viewModelScope.launch {
            when (val outcome = productRepository.setAvailability(product.id, !product.availability)) {
                is Outcome.Success -> _uiState.update { state ->
                    state.copy(
                        products = state.products.map { if (it.id == product.id) outcome.value else it },
                        togglingProductId = null,
                        actionError = null,
                    )
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(togglingProductId = null, actionError = outcome.error)
                }
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val outcome = categoryRepository.listCategories()) {
                is Outcome.Success -> _uiState.update { it.copy(categories = outcome.value) }
                // The facet is a convenience: if it fails, the list still works.
                is Outcome.Failure -> Unit
            }
        }
    }

    private fun load(reset: Boolean, mode: LoadMode) {
        loadJob?.cancel()
        val query: ProductQuery = _uiState.value.query
        val targetPage = if (reset) 0 else loadedPage + 1

        _uiState.update { current ->
            when (mode) {
                LoadMode.Skeleton -> current.copy(
                    phase = ContentPhase.Loading,
                    products = emptyList(),
                    canLoadMore = false,
                    error = null,
                    loadMoreError = null,
                )

                LoadMode.Refresh -> current.copy(isRefreshing = true, error = null)

                LoadMode.Append -> current.copy(isLoadingMore = true, loadMoreError = null)
            }
        }

        loadJob = viewModelScope.launch {
            when (val outcome = productRepository.listProducts(query, targetPage)) {
                is Outcome.Success -> onLoaded(page = outcome.value, reset = reset, pageIndex = targetPage)
                is Outcome.Failure -> onFailed(error = outcome.error, mode = mode)
            }
        }
    }

    private fun onLoaded(page: List<Product>, reset: Boolean, pageIndex: Int) {
        loadedPage = pageIndex
        _uiState.update { current ->
            val merged = if (reset) page else current.products + page
            current.copy(
                phase = if (merged.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                products = merged,
                isRefreshing = false,
                isLoadingMore = false,
                canLoadMore = page.size >= ProductRepository.DEFAULT_PAGE_SIZE,
                error = null,
                loadMoreError = null,
            )
        }
    }

    private fun onFailed(error: AppError, mode: LoadMode) {
        _uiState.update { current ->
            if (mode == LoadMode.Append) {
                current.copy(isLoadingMore = false, loadMoreError = error)
            } else {
                current.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    phase = if (current.products.isEmpty()) ContentPhase.Error else current.phase,
                    error = error,
                )
            }
        }
    }

    private enum class LoadMode { Skeleton, Refresh, Append }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
