package com.vedant.admin.feature.orders.list

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.testing.FakeOrderRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testSummary
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T15 acceptance. Pins the list's four explicit phases, the page-append
 * behaviour and the "a short page means there is nothing more" rule — all
 * against a fake repository, no device required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeOrderRepository()

    private fun viewModel() = OrdersListViewModel(repository)

    @Test
    fun `start loads the first page into the Data phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.listResults = listOf(
                Outcome.Success(List(20) { testSummary(id = "o$it") }),
            )
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()

            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertEquals(20, vm.uiState.value.orders.size)
            assertTrue(vm.uiState.value.canLoadMore)
        }

    @Test
    fun `an empty first page is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.listResults = listOf(Outcome.Success(emptyList()))
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun `a failed first page is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.listResults = listOf(Outcome.Failure(AppError.Network))
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.error)
        }

    @Test
    fun `start applies the initial status filter`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()

            vm.start(OrderStatus.CONFIRMED)
            advanceUntilIdle()

            assertEquals(OrderStatus.CONFIRMED, vm.uiState.value.filter.status)
            assertEquals(OrderStatus.CONFIRMED, repository.listRequests.first().first.status)
        }

    @Test
    fun `loadMore appends the next page and stops on a short page`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.listResults = listOf(
                Outcome.Success(List(20) { testSummary(id = "o$it") }),
                Outcome.Success(listOf(testSummary(id = "extra"))),
            )
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            assertEquals(21, vm.uiState.value.orders.size)
            assertFalse("a short page means the server has nothing more", vm.uiState.value.canLoadMore)
        }

    @Test
    fun `a failed load-more keeps the loaded rows and records the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.listResults = listOf(
                Outcome.Success(List(20) { testSummary(id = "o$it") }),
                Outcome.Failure(AppError.Network),
            )
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("the list is intact", 20, state.orders.size)
            assertEquals(ContentPhase.Data, state.phase)
            assertEquals(AppError.Network, state.loadMoreError)
        }

    @Test
    fun `selecting a status reloads from the first page`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()

            vm.onStatusSelected(OrderStatus.DELIVERED)
            advanceUntilIdle()

            assertEquals(OrderStatus.DELIVERED, vm.uiState.value.filter.status)
            assertEquals("reload starts at page 0", 0, repository.listRequests.last().second)
        }
}
