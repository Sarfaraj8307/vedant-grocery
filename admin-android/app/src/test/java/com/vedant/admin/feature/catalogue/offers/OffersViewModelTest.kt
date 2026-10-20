package com.vedant.admin.feature.catalogue.offers

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.testing.FakeOfferRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.plusDays
import com.vedant.admin.testing.testOffer
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
 * T20 acceptance for offers. Pins the date-range presentation (Active /
 * Scheduled / Expired / Disabled), the local validation guards and the
 * "a rejected write keeps the sheet open and the list untouched" invariant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OffersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val offers = FakeOfferRepository()

    private fun viewModel() = OffersViewModel(offers)

    @Test
    fun `an empty list is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.listResult = Outcome.Success(emptyList())
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun `a failed load is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.listResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    @Test
    fun `each offer reads as Active, Scheduled, Expired or Disabled`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val now = AppClock.now()
            offers.listResult = Outcome.Success(
                listOf(
                    testOffer(id = "expired", endsAt = now.plusDays(-1)),
                    testOffer(id = "live", startsAt = now.plusDays(-1), endsAt = now.plusDays(1)),
                    testOffer(id = "scheduled", startsAt = now.plusDays(1)),
                    testOffer(id = "disabled", isActive = false),
                ),
            )
            val vm = viewModel()

            vm.start("p1")
            advanceUntilIdle()

            val state = vm.uiState.value
            fun label(id: String) = state.offers.first { it.id == id }.statusLabel(state.now)
            assertEquals("Expired", label("expired"))
            assertEquals("Active", label("live"))
            assertEquals("Scheduled", label("scheduled"))
            assertEquals("Disabled", label("disabled"))
        }

    @Test
    fun `a blank title is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onDiscountChange("20")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, offers.createCalls)
        }

    @Test
    fun `a discount outside 1-99 is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onTitleChange("Too good")
            vm.onDiscountChange("120")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, offers.createCalls)
        }

    @Test
    fun `an offer with neither a discount nor a price is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onTitleChange("Nothing off")

            vm.save()
            advanceUntilIdle()

            assertEquals("Set a discount % or a price.", vm.uiState.value.editor?.fieldError)
            assertEquals(0, offers.createCalls)
        }

    @Test
    fun `an end date at or before the start is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val now = AppClock.now()
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onTitleChange("Backwards")
            vm.onDiscountChange("10")
            vm.onStartsAtChange(now.plusDays(2))
            vm.onEndsAtChange(now.plusDays(1))

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, offers.createCalls)
        }

    @Test
    fun `a successful create closes the sheet and re-reads the list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.listResult = Outcome.Success(listOf(testOffer(id = "of1")))
            offers.createResult = Outcome.Success(testOffer(id = "of2"))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.openCreate()
            vm.onTitleChange("Diwali")
            vm.onDiscountChange("25")
            vm.save()
            advanceUntilIdle()

            assertNull(vm.uiState.value.editor)
            assertEquals(1, offers.createCalls)
            assertEquals(25, offers.lastWrittenDraft?.discountPercent)
            assertEquals("p1", offers.lastWrittenDraft?.productId)
        }

    @Test
    fun `a failed create keeps the sheet open with the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.createResult = Outcome.Failure(AppError.Validation("bad window"))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()
            vm.openCreate()
            vm.onTitleChange("Diwali")
            vm.onDiscountChange("25")

            vm.save()
            advanceUntilIdle()

            val editor = vm.uiState.value.editor
            assertNotNull("the sheet stays open so nothing is lost", editor)
            assertEquals(AppError.Validation("bad window"), editor?.error)
        }

    @Test
    fun `a delete removes only the deleted row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.listResult = Outcome.Success(listOf(testOffer(id = "of1"), testOffer(id = "of2")))
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.delete("of1")
            advanceUntilIdle()

            assertEquals(1, offers.deleteCalls)
            assertEquals(listOf("of2"), vm.uiState.value.offers.map { it.id })
        }

    @Test
    fun `a failed delete keeps the row and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            offers.listResult = Outcome.Success(listOf(testOffer(id = "of1")))
            offers.deleteResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start("p1")
            advanceUntilIdle()

            vm.delete("of1")
            advanceUntilIdle()

            assertEquals(listOf("of1"), vm.uiState.value.offers.map { it.id })
            assertEquals(AppError.Unauthorized, vm.uiState.value.actionError)
        }
}
