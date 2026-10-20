package com.vedant.admin.feature.catalogue.editor

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.FakeProductRepository
import com.vedant.admin.testing.FakeWeightRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testProduct
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T20 acceptance for the product editor. Pins the "never fake success" contract
 * (a save adopts only the server-echoed row), the validation guards, and the
 * image rule (an image counts as set only once the server-confirmed URL is
 * persisted on the row) — all against fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val products = FakeProductRepository()
    private val categories = FakeCategoryRepository()
    private val weights = FakeWeightRepository()

    private fun viewModel() = ProductEditorViewModel(products, categories, weights)

    @Test
    fun `start with no id is a create with no baseline`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()

            vm.start(null)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ContentPhase.Data, state.phase)
            assertTrue(state.isCreate)
            assertNull(state.baseline)
            assertFalse("a blank create is not dirty", state.isDirty)
        }

    @Test
    fun `start with an id loads the row into draft and baseline`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Success(testProduct(id = "p1", name = "Tomatoes", basePrice = Money(4_000)))
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("p1", state.productId)
            assertEquals("Tomatoes", state.draft.name)
            assertEquals("40.00", state.basePriceText)
            assertEquals("the loaded row is the baseline", state.baseline, state.draft)
            assertFalse(state.isDirty)
        }

    @Test
    fun `a failed load is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    @Test
    fun `a blank name is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.nameError)
            assertEquals(0, products.createCalls)
        }

    @Test
    fun `an unparseable price is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()
            vm.onNameChange("Okra")
            vm.onBasePriceChange("four rupees")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.priceError)
            assertEquals(0, products.createCalls)
        }

    @Test
    fun `a successful create adopts the server-echoed row as the new baseline`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.createResult = Outcome.Success(testProduct(id = "new", name = "Okra", basePrice = Money(3_000)))
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()
            vm.onNameChange("Okra")
            vm.onBasePriceChange("30.00")

            vm.save()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, products.createCalls)
            assertEquals("new", state.productId)
            assertEquals("Okra", state.draft.name)
            assertNotNull(state.baseline)
            assertEquals("the echoed row becomes the baseline", state.baseline, state.draft)
            assertTrue(state.savedOnce)
            assertFalse("nothing to save after a confirmed save", state.isDirty)
        }

    @Test
    fun `a failed save leaves the typed draft untouched and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.createResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()
            vm.onNameChange("Okra")
            vm.onBasePriceChange("30.00")

            vm.save()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("nothing the operator typed is lost", "Okra", state.draft.name)
            assertEquals(ActionState.Failed(AppError.Network), state.saveState)
            assertNull("no baseline is invented on a failure", state.baseline)
        }

    @Test
    fun `isDirty is a real comparison against the server row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Success(testProduct(id = "p1", name = "Tomatoes"))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isDirty)

            vm.onNameChange("Tomatoes and onions")

            assertTrue(vm.uiState.value.isDirty)
            assertTrue(vm.uiState.value.canSave)
        }

    @Test
    fun `an image picked before the product is saved is refused and never uploaded`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start(null)
            advanceUntilIdle()

            vm.onImagePicked(byteArrayOf(1, 2, 3), "image/jpeg")
            advanceUntilIdle()

            assertEquals("no bytes reach storage", 0, products.uploadCalls)
            assertTrue(vm.uiState.value.upload is UploadState.Failed)
        }

    @Test
    fun `an upload reports progress and is only set once the product write echoes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Success(testProduct(id = "p1", imageUrl = null))
            products.uploadResult = Outcome.Success("https://cdn.example/products/p1/x.jpg")
            products.updateResult = Outcome.Success(
                testProduct(id = "p1", imageUrl = "https://cdn.example/products/p1/x.jpg"),
            )
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasImage)

            vm.onImagePicked(byteArrayOf(1, 2, 3, 4), "image/jpeg")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, products.uploadCalls)
            assertEquals("image/jpeg", products.lastUploadedMime)
            assertEquals("the URL is persisted on the row", "https://cdn.example/products/p1/x.jpg", products.lastWrittenDraft?.imageUrl)
            assertEquals("the URL is only set after the write echoes", "https://cdn.example/products/p1/x.jpg", state.draft.imageUrl)
            assertEquals(UploadState.Idle, state.upload)
            assertTrue(state.hasImage)
        }

    @Test
    fun `a failed upload keeps the bytes and exposes a retry that succeeds`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Success(testProduct(id = "p1", imageUrl = null))
            products.uploadResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.onImagePicked(byteArrayOf(9, 9, 9), "image/png")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.upload is UploadState.Failed)
            assertNull("no image is set on a failed upload", vm.uiState.value.draft.imageUrl)

            products.uploadResult = Outcome.Success("https://cdn.example/products/p1/retry.jpg")
            products.updateResult = Outcome.Success(
                testProduct(id = "p1", imageUrl = "https://cdn.example/products/p1/retry.jpg"),
            )
            vm.retryUpload()
            advanceUntilIdle()

            assertEquals("the retry re-sends the same bytes", 2, products.uploadCalls)
            assertEquals("https://cdn.example/products/p1/retry.jpg", vm.uiState.value.draft.imageUrl)
        }

    @Test
    fun `removing an image uses the explicit clear path and updates the baseline`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            products.getResult = Outcome.Success(testProduct(id = "p1", imageUrl = "https://cdn.example/old.jpg"))
            products.clearImageResult = Outcome.Success(testProduct(id = "p1", imageUrl = null))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.hasImage)

            vm.removeImage()
            advanceUntilIdle()

            assertEquals(1, products.clearImageCalls)
            assertNull(vm.uiState.value.draft.imageUrl)
            assertNull(vm.uiState.value.baseline?.imageUrl)
            assertFalse(vm.uiState.value.hasImage)
        }
}
