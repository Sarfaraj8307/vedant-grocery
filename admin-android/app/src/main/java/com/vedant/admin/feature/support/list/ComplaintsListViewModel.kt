package com.vedant.admin.feature.support.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.repository.ComplaintRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the complaints list: pagination, the status facet and debounced search.
 *
 * The **status facet is applied server-side** (an evidenced enum, `docs/CONTRACT.md`
 * §4). The **free-text search is applied over the loaded page**, inside the
 * repository — the `complaints` columns are BLOCKED (R1), so a server-side `ilike`
 * would have to name a column we cannot verify, and a wrong name makes PostgREST
 * reject the whole request. That trade-off is documented in `ComplaintRepositoryImpl`;
 * this ViewModel simply reloads on the debounced term.
 *
 * The ViewModel holds no business rules beyond "ask the repository, then render
 * what it returns".
 */
class ComplaintsListViewModel(private val complaintRepository: ComplaintRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ComplaintsListUiState())
    val uiState: StateFlow<ComplaintsListUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadedPage = -1
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    /** Idempotent: a recomposition (or a config change) does not re-fetch. */
    fun start() {
        if (started) return
        started = true
        load(reset = true, mode = LoadMode.Skeleton)
    }

    fun onStatusSelected(status: ComplaintStatus?) {
        if (_uiState.value.filter.status == status) return
        _uiState.update { it.copy(filter = it.filter.copy(status = status)) }
        load(reset = true, mode = LoadMode.Skeleton)
    }

    /** Updates the field immediately, then reloads once typing pauses. */
    fun onSearchChange(query: String) {
        _uiState.update { it.copy(filter = it.filter.copy(search = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true, mode = LoadMode.Skeleton)
        }
    }

    fun refresh() = load(reset = true, mode = LoadMode.Refresh)

    fun retry() = load(reset = true, mode = LoadMode.Skeleton)

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingMore || state.phase != ContentPhase.Data) return
        load(reset = false, mode = LoadMode.Append)
    }

    private fun load(reset: Boolean, mode: LoadMode) {
        loadJob?.cancel()
        val filter: ComplaintFilter = _uiState.value.filter
        val targetPage = if (reset) 0 else loadedPage + 1

        _uiState.update { current ->
            when (mode) {
                LoadMode.Skeleton -> current.copy(
                    phase = ContentPhase.Loading,
                    complaints = emptyList(),
                    canLoadMore = false,
                    error = null,
                    loadMoreError = null,
                )

                LoadMode.Refresh -> current.copy(isRefreshing = true, error = null)

                LoadMode.Append -> current.copy(isLoadingMore = true, loadMoreError = null)
            }
        }

        loadJob = viewModelScope.launch {
            when (val outcome = complaintRepository.listComplaints(filter, targetPage)) {
                is Outcome.Success -> onLoaded(page = outcome.value, reset = reset, pageIndex = targetPage)
                is Outcome.Failure -> onFailed(error = outcome.error, mode = mode)
            }
        }
    }

    private fun onLoaded(page: List<Complaint>, reset: Boolean, pageIndex: Int) {
        loadedPage = pageIndex
        _uiState.update { current ->
            val merged = if (reset) page else current.complaints + page
            current.copy(
                phase = if (merged.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                complaints = merged,
                isRefreshing = false,
                isLoadingMore = false,
                // A short page means the server has nothing more to give.
                canLoadMore = page.size >= ComplaintRepository.DEFAULT_PAGE_SIZE,
                error = null,
                loadMoreError = null,
            )
        }
    }

    private fun onFailed(error: AppError, mode: LoadMode) {
        _uiState.update { current ->
            if (mode == LoadMode.Append) {
                // The list is intact; only the extra page failed.
                current.copy(isLoadingMore = false, loadMoreError = error)
            } else {
                current.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    // Keep the rows if we had them; otherwise show the error state.
                    phase = if (current.complaints.isEmpty()) ContentPhase.Error else current.phase,
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
