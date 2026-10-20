package com.vedant.admin.feature.support.thread

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.testing.FakeComplaintRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testAttachment
import com.vedant.admin.testing.testComplaint
import com.vedant.admin.testing.testMessage
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.CompletableDeferred
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
 * T22 acceptance for the complaint thread. Pins the three non-negotiables on the
 * write paths: **never fake success**, **no double-submit**, and **the offered
 * transitions are a convenience**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComplaintThreadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val complaints = FakeComplaintRepository()

    private fun viewModel() = ComplaintThreadViewModel(complaints)

    @Test
    fun `start loads the complaint, its messages and its attachments`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1"))
            complaints.messagesResult = Outcome.Success(listOf(testMessage(id = "m1")))
            complaints.attachmentsResult = Outcome.Success(listOf(testAttachment()))
            val vm = viewModel()

            vm.start("c1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ContentPhase.Data, state.phase)
            assertEquals("c1", state.complaint?.id)
            assertEquals(1, state.messages.size)
            assertEquals(1, state.attachments.size)
        }

    @Test
    fun `a failed load is the Error phase`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.getResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()

        vm.start("c1")
        advanceUntilIdle()

        assertEquals(ContentPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.Network, vm.uiState.value.loadError)
    }

    @Test
    fun `a sent reply appends only the server's message and clears the draft`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1"))
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.onReplyChange("We are sorry.")
            vm.sendReply()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, complaints.replyCalls)
            assertEquals("We are sorry.", complaints.lastReplyBody)
            assertEquals(1, state.messages.size)
            assertEquals("", state.replyDraft)
        }

    @Test
    fun `a failed reply keeps the draft and appends nothing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1"))
            complaints.replyResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.onReplyChange("We are sorry.")
            vm.sendReply()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("nothing may be shown as sent", state.messages.isEmpty())
            assertEquals("the draft is kept", "We are sorry.", state.replyDraft)
            assertEquals(AppError.Network, (state.replyState as com.vedant.admin.ui.components.ActionState.Failed).error)
        }

    @Test
    fun `a blank reply is refused without a request`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start("c1")
        advanceUntilIdle()

        vm.onReplyChange("   ")
        vm.sendReply()
        advanceUntilIdle()

        assertEquals(0, complaints.replyCalls)
        assertNotNull(vm.uiState.value.composerError)
    }

    @Test
    fun `a double submit is blocked while a reply is in flight`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1"))
            complaints.gate = CompletableDeferred()
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.onReplyChange("First")
            vm.sendReply()
            // The reply is now suspended on the gate; a second send must be refused.
            vm.sendReply()

            assertEquals(1, complaints.replyCalls)
            assertTrue(vm.uiState.value.isReplyInFlight)

            complaints.gate?.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isReplyInFlight)
        }

    @Test
    fun `a status change replaces the complaint with the server's row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1", status = ComplaintStatus.OPEN))
            complaints.statusResult = Outcome.Success(testComplaint(id = "c1", status = ComplaintStatus.RESOLVED))
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.changeStatus(ComplaintStatus.RESOLVED)
            advanceUntilIdle()

            assertEquals(ComplaintStatus.RESOLVED, vm.uiState.value.complaint?.status)
            assertEquals(ComplaintStatus.RESOLVED, complaints.lastStatus)
        }

    @Test
    fun `a failed status change leaves the complaint untouched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1", status = ComplaintStatus.OPEN))
            complaints.statusResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.changeStatus(ComplaintStatus.CLOSED)
            advanceUntilIdle()

            assertEquals("the UI must not move", ComplaintStatus.OPEN, vm.uiState.value.complaint?.status)
            assertEquals(AppError.Unauthorized, vm.uiState.value.actionError)
        }

    @Test
    fun `an attachment upload re-reads the server's attachments`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            complaints.getResult = Outcome.Success(testComplaint(id = "c1"))
            complaints.uploadResult = Outcome.Success(testAttachment())
            complaints.attachmentsResult = Outcome.Success(listOf(testAttachment(id = "a9")))
            val vm = viewModel()
            vm.start("c1")
            advanceUntilIdle()

            vm.attachFile("photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
            advanceUntilIdle()

            assertEquals(1, complaints.uploadCalls)
            assertEquals(listOf("a9"), vm.uiState.value.attachments.map { it.id })
            assertNull(vm.uiState.value.attachmentName)
        }

    @Test
    fun `a closed complaint cannot be replied to`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        complaints.getResult = Outcome.Success(testComplaint(id = "c1", status = ComplaintStatus.CLOSED))
        val vm = viewModel()
        vm.start("c1")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canReply)
    }
}
