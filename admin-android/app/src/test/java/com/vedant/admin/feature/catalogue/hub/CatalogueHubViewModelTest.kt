package com.vedant.admin.feature.catalogue.hub

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.FakeProductRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testCategory
import com.vedant.admin.testing.testProduct
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T20 acceptance for the catalogue hub. Pins the four explicit phases, the
 * server-side facets and debounced search, page append, and the "an availability
 * toggle only moves after the server confirms it" invariant — all against fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueHubViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val products = FakeProductRepository()
    private val categories = FakeCategoryRepository()

    private fun viewModel() = CatalogueHubViewModel(products, categories)

    @Test
    fun `start loads the first page into the Data phase and the category facet`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(List(20) { testProduct(id = "p$it") }))
            categories.listResult = Outcome.Success(listOf(testCategory(id = "c1", name = "Vegetables")))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertEquals(20, vm.uiState.value.products.size)
            assertTrue(vm.uiState.value.canLoadMore)
            assertEquals("Vegetables", vm.uiState.value.categoryName("c1"))
        }

    @Test
    fun `an empty first page is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(emptyList()))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
            assertFalse(vm.uiState.value.isEmptyBecauseFiltered)
        }

    @Test
    fun `a failed first page is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Failure(AppError.Network))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.error)
        }

    @Test
    fun `a failed category facet leaves the list working`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(listOf(testProduct())))
            categories.listResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.categories.isEmpty())
        }

    @Test
    fun `selecting a category reloads from page 0 with the facet applied server-side`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onCategorySelected("c2")
            advanceUntilIdle()

            assertEquals("c2", vm.uiState.value.query.categoryId)
            val (query, page) = products.listRequests.last()
            assertEquals("c2", query.categoryId)
            assertEquals("a facet reload starts at page 0", 0, page)
        }

    @Test
    fun `search is applied to the server after a debounce, from page 0`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            val before = products.listRequests.size

            vm.onSearchChange("tom")
            assertEquals("typing updates the field immediately", "tom", vm.uiState.value.query.search)
            assertEquals("but does not fire a request yet", before, products.listRequests.size)

            advanceUntilIdle()

            val (query, page) = products.listRequests.last()
            assertEquals("tom", query.search)
            assertEquals(0, page)
        }

    @Test
    fun `loadMore appends the next page and stops on a short page`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(
                Outcome.Success(List(20) { testProduct(id = "p$it") }),
                Outcome.Success(listOf(testProduct(id = "extra"))),
            )
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            assertEquals(21, vm.uiState.value.products.size)
            assertFalse("a short page means there is nothing more", vm.uiState.value.canLoadMore)
        }

    @Test
    fun `a failed load-more keeps the loaded rows and records the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(
                Outcome.Success(List(20) { testProduct(id = "p$it") }),
                Outcome.Failure(AppError.Network),
            )
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("the list is intact", 20, state.products.size)
            assertEquals(ContentPhase.Data, state.phase)
            assertEquals(AppError.Network, state.loadMoreError)
        }

    @Test
    fun `toggling availability replaces the row with the server-confirmed row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(listOf(testProduct(id = "p1", availability = true))))
            products.setAvailabilityResult = Outcome.Success(testProduct(id = "p1", availability = false))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.toggleAvailability(vm.uiState.value.products.first())
            advanceUntilIdle()

            assertFalse(vm.uiState.value.products.first().availability)
            assertNull(vm.uiState.value.togglingProductId)
            assertEquals(1, products.setAvailabilityCalls)
        }

    @Test
    fun `a second toggle is ignored while the first is in flight`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(listOf(testProduct(id = "p1", availability = true))))
            val gate = CompletableDeferred<Unit>()
            products.availabilityGate = gate
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.toggleAvailability(vm.uiState.value.products.first())
            assertEquals("p1", vm.uiState.value.togglingProductId)
            vm.toggleAvailability(vm.uiState.value.products.first())

            assertEquals("the second tap is dropped", 1, products.setAvailabilityCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(vm.uiState.value.togglingProductId)
        }

    @Test
    fun `a rejected toggle leaves the row exactly as the server last returned it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(listOf(testProduct(id = "p1", availability = true))))
            products.setAvailabilityResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.toggleAvailability(vm.uiState.value.products.first())
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("the row does not move on a rejection", state.products.first().availability)
            assertEquals(AppError.Unauthorized, state.actionError)
            assertNull(state.togglingProductId)
        }

    @Test
    fun `an empty page under an active filter is flagged as filtered`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.listResults = listOf(Outcome.Success(emptyList()))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()
            vm.onAvailabilitySelected(false)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ContentPhase.Empty, state.phase)
            assertTrue(state.isEmptyBecauseFiltered)
        }
}
