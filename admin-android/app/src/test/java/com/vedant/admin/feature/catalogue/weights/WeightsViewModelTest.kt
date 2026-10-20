package com.vedant.admin.feature.catalogue.weights

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeWeightRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testWeight
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * T20 acceptance for the inline weight-variant editor. Pins the empty/data/error
 * phases, the field guards, the deterministic ordering after a save, and the
 * "a rejected write keeps the form open and the list untouched" invariant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val weights = FakeWeightRepository()

    private fun viewModel() = WeightsViewModel(weights)

    @Test
    fun `an empty variant list is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Success(emptyList())
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun `a failed load is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    @Test
    fun `a blank label is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onPriceChange("40.00")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editing?.fieldError)
            assertEquals(0, weights.saveCalls)
        }

    @Test
    fun `an unparseable price is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onLabelChange("500 g")
            vm.onPriceChange("free")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editing?.fieldError)
            assertEquals(0, weights.saveCalls)
        }

    @Test
    fun `a non-numeric gram weight is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onLabelChange("Half kilo")
            vm.onGramsChange("half")
            vm.onPriceChange("40.00")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editing?.fieldError)
            assertEquals(0, weights.saveCalls)
        }

    @Test
    fun `a successful save appends the confirmed variant and sorts by grams`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Success(listOf(testWeight(id = "w1", label = "1 kg", grams = 1000)))
            weights.saveResult = Outcome.Success(testWeight(id = "w2", label = "500 g", grams = 500))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.openCreate()
            vm.onLabelChange("500 g")
            vm.onGramsChange("500")
            vm.onPriceChange("40.00")
            vm.save()
            advanceUntilIdle()

            assertEquals(1, weights.saveCalls)
            assertEquals(500, weights.lastWrittenDraft?.grams)
            assertNull(vm.uiState.value.editing)
            assertEquals("sorted ascending by grams", listOf("w2", "w1"), vm.uiState.value.weights.map { it.id })
        }

    @Test
    fun `a failed save keeps the form open and the list untouched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Success(listOf(testWeight(id = "w1")))
            weights.saveResult = Outcome.Failure(AppError.Validation("bad"))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onLabelChange("500 g")
            vm.onPriceChange("40.00")

            vm.save()
            advanceUntilIdle()

            val editing = vm.uiState.value.editing
            assertNotNull("the form stays open so nothing is lost", editing)
            assertEquals(AppError.Validation("bad"), editing?.error)
            assertEquals("the list is untouched", listOf("w1"), vm.uiState.value.weights.map { it.id })
        }

    @Test
    fun `a delete removes only the deleted variant`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Success(listOf(testWeight(id = "w1"), testWeight(id = "w2")))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.delete("w1")
            advanceUntilIdle()

            assertEquals(1, weights.deleteCalls)
            assertEquals(listOf("w2"), vm.uiState.value.weights.map { it.id })
        }

    @Test
    fun `a failed delete keeps the variant and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            weights.listResult = Outcome.Success(listOf(testWeight(id = "w1")))
            weights.deleteResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.delete("w1")
            advanceUntilIdle()

            assertEquals(listOf("w1"), vm.uiState.value.weights.map { it.id })
            assertEquals(AppError.Unauthorized, vm.uiState.value.actionError)
        }
}
