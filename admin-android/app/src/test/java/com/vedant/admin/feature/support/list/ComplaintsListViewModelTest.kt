package com.vedant.admin.feature.support.list

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.testing.FakeComplaintRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testComplaint
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * T22 acceptance for the complaints list. Pins the four content phases, the
 * server-side status facet, the debounced search and the "a failed page never
 * blanks the rows" rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComplaintsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val complaints = FakeComplaintRepository()

    private fun viewModel() = ComplaintsListViewModel(complaints)

    @Test
    fun `start loads the first page`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Success(listOf(testComplaint(id = "c1")))
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ContentPhase.Data, state.phase)
        assertEquals(1, state.complaints.size)
        assertEquals(0, complaints.listRequests.first().second)
    }

    @Test
    fun `an empty page is the Empty phase`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Success(emptyList())
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
    }

    @Test
    fun `a failed load is the Error phase`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(ContentPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.Network, vm.uiState.value.error)
    }

    @Test
    fun `selecting a status filters on the server`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Success(listOf(testComplaint()))
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.onStatusSelected(ComplaintStatus.IN_PROGRESS)
        advanceUntilIdle()

        assertEquals(ComplaintStatus.IN_PROGRESS, complaints.listRequests.last().first.status)
    }

    @Test
    fun `the search reloads after the debounce`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Success(emptyList())
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        val before = complaints.listRequests.size

        vm.onSearchChange("tomato")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals("a reload happened once typing paused", before + 1, complaints.listRequests.size)
        assertEquals("tomato", complaints.listRequests.last().first.search)
    }

    @Test
    fun `loadMore appends the next page`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        // A full first page makes canLoadMore true.
        complaints.listResult = Outcome.Success(List(20) { testComplaint(id = "c$it") })
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()
        assertEquals(20, vm.uiState.value.complaints.size)

        complaints.listResult = Outcome.Success(listOf(testComplaint(id = "c20")))
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(21, vm.uiState.value.complaints.size)
    }

    @Test
    fun `a failed load more keeps the rows on screen`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.listResult = Outcome.Success(List(20) { testComplaint(id = "c$it") })
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        complaints.listResult = Outcome.Failure(AppError.Network)
        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("the list is intact", 20, state.complaints.size)
        assertNotNull(state.loadMoreError)
    }
}
