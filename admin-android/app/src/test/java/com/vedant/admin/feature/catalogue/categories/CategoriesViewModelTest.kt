package com.vedant.admin.feature.catalogue.categories

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testCategory
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
 * T20 acceptance for categories. Pins the empty/data/error phases, the create
 * guard, and the "a rejected write leaves the list exactly as the server last
 * returned it" invariant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categories = FakeCategoryRepository()

    private fun viewModel() = CategoriesViewModel(categories)

    @Test
    fun `an empty list is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.listResult = Outcome.Success(emptyList())
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun `a loaded list is the Data phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.listResult = Outcome.Success(listOf(testCategory(id = "c1", name = "Vegetables")))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertEquals("Vegetables", vm.uiState.value.categories.first().name)
        }

    @Test
    fun `a failed load is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.listResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    @Test
    fun `a blank name is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.edit?.nameError)
            assertEquals(0, categories.createCalls)
        }

    @Test
    fun `a successful create closes the sheet and re-reads the list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.createResult = Outcome.Success(testCategory(id = "c2", name = "Fruits", glyph = "\uD83C\uDF4E"))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.openCreate()
            vm.onNameChange("Fruits")
            vm.onGlyphChange("\uD83C\uDF4E")
            vm.save()
            advanceUntilIdle()

            assertNull(vm.uiState.value.edit)
            assertEquals(1, categories.createCalls)
            assertEquals("Fruits", categories.lastWrittenDraft?.name)
        }

    @Test
    fun `a failed create keeps the sheet open with the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.createResult = Outcome.Failure(AppError.Validation("duplicate"))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()
            vm.onNameChange("Fruits")

            vm.save()
            advanceUntilIdle()

            val edit = vm.uiState.value.edit
            assertNotNull("the sheet stays open so nothing is lost", edit)
            assertEquals(AppError.Validation("duplicate"), edit?.error)
        }

    @Test
    fun `confirming a delete removes only that row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.listResult = Outcome.Success(
                listOf(testCategory(id = "c1"), testCategory(id = "c2")),
            )
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.requestDelete(vm.uiState.value.categories.first { it.id == "c1" })
            vm.confirmDelete()
            advanceUntilIdle()

            assertEquals(1, categories.deleteCalls)
            assertEquals(listOf("c2"), vm.uiState.value.categories.map { it.id })
            assertNull(vm.uiState.value.pendingDelete)
        }

    @Test
    fun `a failed delete keeps the row and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            categories.listResult = Outcome.Success(listOf(testCategory(id = "c1")))
            categories.deleteResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.requestDelete(vm.uiState.value.categories.first())
            vm.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("c1"), vm.uiState.value.categories.map { it.id })
            assertEquals(AppError.Unauthorized, vm.uiState.value.actionError)
            assertNull(vm.uiState.value.deletingId)
        }
}
