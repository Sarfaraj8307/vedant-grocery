package com.vedant.admin.feature.delivery

import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.domain.model.DeliveryIssueType
import com.vedant.admin.testing.MainDispatcherRule
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
 * The courier ViewModel, focused on the REPORT ISSUE flow (§25): validation
 * before the request, a real success/failure state either side of it, and the
 * dialog's draft surviving dismissal logic. The RPC is a fake — authorization
 * is the migration's RLS job, proven server-side, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rpc = FakeDeliveryRpc()

    private fun viewModel() = DeliveryViewModel(rpc)

    private fun DeliveryViewModel.opened(type: DeliveryIssueType, reason: String) {
        openIssueReport(ORDER_ID)
        onIssueTypeSelected(type)
        onIssueReasonChange(reason)
    }

    @Test
    fun `a blank reason never leaves the device`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.DAMAGED_ITEM, "   ")

            vm.submitIssueReport()
            advanceUntilIdle()

            assertTrue("no request may be sent", rpc.reported.isEmpty())
            assertNotNull("the dialog must explain the problem", vm.uiState.value.issueError)
            assertFalse(vm.uiState.value.issueBusy)
            assertEquals(ORDER_ID, vm.uiState.value.issueOrderId) // still open
        }

    @Test
    fun `a reason past the length limit is refused client-side`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.OTHER, "x".repeat(DeliveryIssueType.entries.size + 1000))

            vm.submitIssueReport()
            advanceUntilIdle()

            assertTrue(rpc.reported.isEmpty())
            assertNotNull(vm.uiState.value.issueError)
        }

    @Test
    fun `a valid report is sent trimmed, with the wire type`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.PHONE_UNREACHABLE, "  rang 4 times, no answer  ")

            vm.submitIssueReport()
            advanceUntilIdle()

            val sent = rpc.reported.singleOrNull()
            assertNotNull(sent)
            assertEquals(ORDER_ID, sent!!.orderId)
            assertEquals("phone_unreachable", sent.type)
            assertEquals("rang 4 times, no answer", sent.reason)
        }

    @Test
    fun `a successful report closes the dialog and confirms in the banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.CUSTOMER_UNAVAILABLE, "Nobody at the gate")

            vm.submitIssueReport()
            advanceUntilIdle()

            assertNull("the dialog must close on success", vm.uiState.value.issueOrderId)
            assertFalse(vm.uiState.value.issueBusy)
            assertNull(vm.uiState.value.issueError)
            assertNotNull(vm.uiState.value.actionMessage)
        }

    @Test
    fun `a failed report keeps the dialog open with the reason`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            rpc.failWith = IllegalStateException("insert refused (42501)")
            val vm = viewModel()
            vm.opened(DeliveryIssueType.PAYMENT_ISSUE, "Cash short by 40")

            vm.submitIssueReport()
            advanceUntilIdle()

            assertEquals(ORDER_ID, vm.uiState.value.issueOrderId) // still open
            assertFalse("the button must not stay in flight", vm.uiState.value.issueBusy)
            assertNotNull(vm.uiState.value.issueError)
            assertNull("a failure is not a success", vm.uiState.value.actionMessage)
        }

    @Test
    fun `a double submit sends exactly one report`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.OTHER, "Gate code changed")

            vm.submitIssueReport()
            vm.submitIssueReport()
            advanceUntilIdle()

            assertEquals(1, rpc.reported.size)
        }

    @Test
    fun `dismiss drops the draft and closes the dialog`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.OTHER, "Typo")
            advanceUntilIdle()

            vm.dismissIssueReport()

            assertNull(vm.uiState.value.issueOrderId)
            assertEquals("", vm.uiState.value.issueReason)
            assertNull(vm.uiState.value.issueError)
        }

    @Test
    fun `re-opening resets the draft to a clean state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.opened(DeliveryIssueType.DAMAGED_ITEM, "Crushed tomatoes")
            vm.dismissIssueReport()

            vm.openIssueReport(ORDER_ID)

            assertEquals(DeliveryIssueType.DEFAULT, vm.uiState.value.issueType)
            assertEquals("", vm.uiState.value.issueReason)
            assertNull(vm.uiState.value.issueError)
        }

    private class FakeDeliveryRpc : AdminRpc {
        data class Report(val orderId: String, val type: String, val reason: String)

        val reported = mutableListOf<Report>()
        var failWith: Throwable? = null

        override suspend fun isAdmin(): Boolean = true

        override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) = Unit

        override suspend fun rejectOrderItem(itemId: String, reason: String) = Unit

        override suspend fun restoreOrderItem(itemId: String) = Unit

        override suspend fun ensureDeliverySlots(from: String, days: Int) = Unit

        override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> = emptyList()

        override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> = emptyList()

        override suspend fun isAcceptingOrders(): Boolean? = true

        override suspend fun setOrderAcceptance(accepting: Boolean, reason: String?) = Unit

        override suspend fun reportDeliveryIssue(orderId: String, type: String, reason: String) {
            failWith?.let { throw it }
            reported += Report(orderId, type, reason)
        }
    }

    private companion object {
        const val ORDER_ID = "11111111-2222-3333-4444-555555555555"
    }
}
