package com.vedant.admin.feature.slots

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeSlotRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testSlot
import com.vedant.admin.testing.testSlotLoad
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
 * T21 acceptance for the delivery-slots screen. Pins the day-schedule load, the
 * "a failed utilisation read must not blank the schedule" rule, the editor
 * guards, and the "a rejected write leaves the schedule untouched" invariant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlotsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val slots = FakeSlotRepository()

    private fun viewModel() = SlotsViewModel(slots)

    @Test
    fun `start reads the window and the utilisation in one pass`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1")))
            slots.loadResult = Outcome.Success(listOf(testSlotLoad(slotId = "s1", capacity = 25, booked = 18)))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ContentPhase.Data, state.phase)
            assertEquals(1, state.slots.size)
            assertEquals(18, state.loadFor("s1")?.booked)
            assertEquals("18 / 25", state.loadFor("s1")?.utilisationLabel)
            assertFalse(state.loadFor("s1")!!.isFull)
            assertEquals("the window is bounded on the IST day", 1, slots.listRequests.size)
        }

    @Test
    fun `a full slot is flagged full with a clamped utilisation`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1", capacity = 25)))
            slots.loadResult = Outcome.Success(listOf(testSlotLoad(slotId = "s1", capacity = 25, booked = 25)))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            val load = vm.uiState.value.loadFor("s1")!!
            assertTrue(load.isFull)
            assertEquals(1f, load.utilisation)
            assertEquals(0, load.remaining)
        }

    @Test
    fun `a failed window read is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    @Test
    fun `a failed utilisation read still renders the schedule`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1")))
            slots.loadResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("the schedule is not blanked by a bonus read", ContentPhase.Data, state.phase)
            assertEquals(1, state.slots.size)
            assertNull("no number is invented", state.loadFor("s1"))
        }

    @Test
    fun `an empty window is the Empty phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(emptyList())
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals(ContentPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun `openCreate pre-fills today's IST date`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.openCreate()

            val editor = vm.uiState.value.editor
            assertNotNull(editor)
            assertEquals("08:00", editor?.startTime)
            assertEquals("10:00", editor?.endTime)
            assertNotNull("a day is pre-filled", editor?.date)
        }

    @Test
    fun `a missing capacity is rejected without touching the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, slots.saveCalls)
        }

    @Test
    fun `a malformed start time is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()
            vm.onCapacityChange("25")
            vm.onStartTimeChange("8am")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, slots.saveCalls)
        }

    @Test
    fun `an end time before the start is rejected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()
            vm.onCapacityChange("25")
            vm.onStartTimeChange("10:00")
            vm.onEndTimeChange("08:00")

            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.editor?.fieldError)
            assertEquals(0, slots.saveCalls)
        }

    @Test
    fun `a successful save closes the editor and re-reads the window`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.saveResult = Outcome.Success(testSlot(id = "s2"))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.openCreate()
            vm.onNameChange("Evening")
            vm.onCapacityChange("25")
            vm.save()
            advanceUntilIdle()

            assertEquals(1, slots.saveCalls)
            assertEquals(25, slots.lastWrittenDraft?.capacity)
            assertNull(vm.uiState.value.editor)
        }

    @Test
    fun `a failed save keeps the editor open with the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.saveResult = Outcome.Failure(AppError.Validation("overlaps"))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()
            vm.openCreate()
            vm.onCapacityChange("25")

            vm.save()
            advanceUntilIdle()

            val editor = vm.uiState.value.editor
            assertNotNull("the editor stays open so nothing is lost", editor)
            assertEquals(AppError.Validation("overlaps"), editor?.error)
        }

    @Test
    fun `a delete removes the slot and its utilisation row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1"), testSlot(id = "s2")))
            slots.loadResult = Outcome.Success(listOf(testSlotLoad(slotId = "s1"), testSlotLoad(slotId = "s2")))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.delete("s1")
            advanceUntilIdle()

            assertEquals(1, slots.deleteCalls)
            assertEquals(listOf("s2"), vm.uiState.value.slots.map { it.id })
            assertNull(vm.uiState.value.loadFor("s1"))
        }

    @Test
    fun `start materialises the window automatically so the dates roll forward`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1")))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            // Opening the screen must top the window up on its own — that is what
            // makes tomorrow appear every day without pressing "generate".
            assertEquals(1, slots.ensureCalls)
            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
        }

    @Test
    fun `ensureSlots materialises the window and re-reads it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.ensureSlots()
            advanceUntilIdle()

            // Two: one from the automatic top-up on open, one from the press.
            assertEquals(2, slots.ensureCalls)
            assertFalse(vm.uiState.value.isEnsuring)
        }

    @Test
    fun `a failed automatic top-up still shows the existing slots`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1")))
            slots.ensureResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            // Topping up failing must never hide the schedule that already exists.
            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertEquals(listOf("s1"), vm.uiState.value.slots.map { it.id })
        }

    @Test
    fun `a failed ensureSlots surfaces the error without disturbing the schedule`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            slots.listResult = Outcome.Success(listOf(testSlot(id = "s1")))
            slots.ensureResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.ensureSlots()
            advanceUntilIdle()

            assertEquals(AppError.Unauthorized, vm.uiState.value.actionError)
            assertEquals(listOf("s1"), vm.uiState.value.slots.map { it.id })
        }
}
