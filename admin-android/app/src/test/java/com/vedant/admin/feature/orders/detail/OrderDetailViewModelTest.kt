package com.vedant.admin.feature.orders.detail

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.testing.FakeOrderRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testItem
import com.vedant.admin.testing.testOrder
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T16 / T17 acceptance, asserted against a fake [FakeOrderRepository].
 *
 * These pin the three rules the brief calls non-negotiable:
 *  - a status change in flight **blocks a second submit** (no double-send);
 *  - a **rejected** status change leaves the order exactly where it was and
 *    surfaces the mapped error;
 *  - a success renders the **server's** re-fetched row — `final_total` verbatim,
 *    never a locally computed number.
 *
 * Plus the item workflow: a rejection needs a reason before any request, and the
 * totals after a reject/restore are the server's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeOrderRepository()

    private fun viewModel() = OrderDetailViewModel(repository)

    // --- Load ---------------------------------------------------------------

    @Test
    fun `start loads the order into the Data phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            val vm = viewModel()

            vm.start("o1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Data, vm.uiState.value.phase)
            assertEquals(OrderStatus.PENDING, vm.uiState.value.order?.status)
            assertEquals(1, repository.getOrderCalls)
        }

    @Test
    fun `start is idempotent for the same order`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()

            vm.start("o1")
            vm.start("o1")
            advanceUntilIdle()

            assertEquals(1, repository.getOrderCalls)
        }

    @Test
    fun `a failed load is the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()

            vm.start("o1")
            advanceUntilIdle()

            assertEquals(ContentPhase.Error, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.loadError)
        }

    // --- Status workflow ----------------------------------------------------

    @Test
    fun `a status change in flight blocks a second submit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            // Hold the first request open so it stays in flight.
            repository.gate = CompletableDeferred()
            repository.updateStatusResult = Outcome.Success(testOrder(status = OrderStatus.CONFIRMED))

            vm.advanceTo(OrderStatus.CONFIRMED)
            assertTrue("the action must be in flight", vm.uiState.value.isStatusInFlight)
            assertEquals(OrderStatus.CONFIRMED, vm.uiState.value.pendingStatus)

            // A second tap while the first is still running must be ignored.
            vm.advanceTo(OrderStatus.CONFIRMED)
            assertEquals("only one request may be sent", 1, repository.updateStatusCalls)

            repository.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.updateStatusCalls)
            assertEquals(OrderStatus.CONFIRMED, vm.uiState.value.order?.status)
        }

    @Test
    fun `a rejected status change leaves the order unchanged and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            repository.updateStatusResult = Outcome.Failure(AppError.Unauthorized)
            vm.advanceTo(OrderStatus.CONFIRMED)
            advanceUntilIdle()

            val state = vm.uiState.value
            // The UI must not move: the rendered status is still the server's.
            assertEquals(OrderStatus.PENDING, state.order?.status)
            assertNull(state.pendingStatus)
            val action = state.statusAction
            assertTrue(action is ActionState.Failed)
            assertEquals(AppError.Unauthorized, (action as ActionState.Failed).error)
        }

    @Test
    fun `a successful status change renders the server's row verbatim`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult =
                Outcome.Success(testOrder(status = OrderStatus.PENDING, finalTotal = Money(15_200)))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            // The server recomputed after a line was rejected: total 20.00.
            repository.updateStatusResult = Outcome.Success(
                testOrder(
                    status = OrderStatus.CONFIRMED,
                    originalTotal = Money(15_200),
                    adjustmentAmount = Money(-13_200),
                    finalTotal = Money(2_000),
                ),
            )

            vm.advanceTo(OrderStatus.CONFIRMED)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(OrderStatus.CONFIRMED, state.order?.status)
            assertEquals(Money(2_000), state.order?.finalTotal)
            assertEquals(ActionState.Idle, state.statusAction)
        }

    @Test
    fun `a transition the UI does not offer is never sent`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            vm.advanceTo(OrderStatus.DELIVERED) // not a next status of PENDING
            advanceUntilIdle()

            assertEquals(0, repository.updateStatusCalls)
        }

    @Test
    fun `cancelling without a reason is refused before any request`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            vm.advanceTo(OrderStatus.CANCELLED, reason = null)
            advanceUntilIdle()

            assertEquals(0, repository.updateStatusCalls)
            val action = vm.uiState.value.statusAction
            assertTrue(action is ActionState.Failed)
            assertTrue((action as ActionState.Failed).error is AppError.Validation)
        }

    // --- Item reject / restore ---------------------------------------------

    @Test
    fun `rejecting an item requires a reason before any request`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.CONFIRMED))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            vm.openRejectSheet(testItem(id = "i1"))
            vm.submitReject()
            advanceUntilIdle()

            assertEquals(0, repository.rejectCalls)
            assertEquals("A reason is required.", vm.uiState.value.rejectSheet?.reasonError)
        }

    @Test
    fun `a reject renders the server's recomputed totals and closes the sheet`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(
                testOrder(status = OrderStatus.CONFIRMED, finalTotal = Money(15_200)),
            )
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            repository.rejectResult = Outcome.Success(
                testOrder(
                    status = OrderStatus.CONFIRMED,
                    adjustmentAmount = Money(-13_200),
                    finalTotal = Money(2_000),
                ),
            )

            vm.openRejectSheet(testItem(id = "i1"))
            vm.onRejectReasonChange("Damaged in transit")
            vm.submitReject()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.rejectSheet)
            assertEquals(Money(2_000), state.order?.finalTotal)
            assertEquals(1, repository.rejectCalls)
        }

    @Test
    fun `a failed reject keeps the sheet open with the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.CONFIRMED))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            repository.rejectResult = Outcome.Failure(AppError.Unauthorized)
            vm.openRejectSheet(testItem(id = "i1"))
            vm.onRejectReasonChange("Damaged")
            vm.submitReject()
            advanceUntilIdle()

            val sheet = vm.uiState.value.rejectSheet
            assertTrue("the sheet stays open", sheet != null)
            assertEquals(AppError.Unauthorized, sheet?.error)
            assertEquals("the order is untouched", Money(15_200), vm.uiState.value.order?.finalTotal)
        }

    @Test
    fun `a restore renders the server's restored totals`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(
                testOrder(
                    status = OrderStatus.CONFIRMED,
                    adjustmentAmount = Money(-13_200),
                    finalTotal = Money(2_000),
                ),
            )
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            repository.restoreResult =
                Outcome.Success(testOrder(status = OrderStatus.CONFIRMED, finalTotal = Money(15_200)))

            vm.restoreItem("i1")
            advanceUntilIdle()

            assertEquals(Money(15_200), vm.uiState.value.order?.finalTotal)
            assertEquals(1, repository.restoreCalls)
        }

    @Test
    fun `items are not adjustable once the order is terminal`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.getOrderResult = Outcome.Success(testOrder(status = OrderStatus.DELIVERED))
            val vm = viewModel()
            vm.start("o1")
            advanceUntilIdle()

            vm.openRejectSheet(testItem(id = "i1"))

            assertNull("no sheet may open on a closed order", vm.uiState.value.rejectSheet)
            assertTrue(vm.uiState.value.nextStatuses.isEmpty())
        }
}
