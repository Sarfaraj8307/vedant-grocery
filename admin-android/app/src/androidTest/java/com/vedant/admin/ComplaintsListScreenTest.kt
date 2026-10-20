package com.vedant.admin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.feature.support.list.ComplaintsListScreen
import com.vedant.admin.feature.support.list.ComplaintsListViewModel
import com.vedant.admin.testing.FakeComplaintRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testComplaint
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S14 — Complaints list. Driven by a fake [FakeComplaintRepository]; no live RPC
 * is made (**BLOCKED (R1)**).
 *
 * The status facet uses the **live** enum (`OPEN|IN_PROGRESS|RESOLVED|CLOSED`) —
 * the retired `IN_REVIEW` must never appear as a filter chip.
 */
@OptIn(ExperimentalTestApi::class)
class ComplaintsListScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var openedComplaint: String? = null

    private fun show(repo: FakeComplaintRepository) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                ComplaintsListScreen(
                    factory = fakeViewModelFactory { ComplaintsListViewModel(repo) },
                    onOpenComplaint = { openedComplaint = it },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun an_empty_page_shows_the_empty_state() {
        val repo = FakeComplaintRepository().apply { listResult = Outcome.Success(emptyList()) }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("No complaints yet"))
        rule.onNodeWithText("Customer complaints appear here when they are raised.").assertExists()
    }

    @Test
    fun a_loaded_complaint_renders_its_title_and_status_word() {
        val repo = FakeComplaintRepository().apply {
            listResult = Outcome.Success(
                listOf(testComplaint(subject = "Missing item", status = ComplaintStatus.OPEN)),
            )
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Missing item"))
        // Status is conveyed by word (and, in the UI, icon + colour).
        // Two nodes legitimately read "Open": the status filter chip and the row's
        // merged text ("Missing item, ..., Open, MISSING_ITEM, Order #..., Asha").
        // Assert both rather than an ambiguous single match.
        rule.onAllNodesWithText("Open", substring = true).assertCountEquals(2)
    }

    @Test
    fun tapping_a_complaint_opens_its_thread() {
        val repo = FakeComplaintRepository().apply {
            listResult = Outcome.Success(listOf(testComplaint(id = "complaint-1")))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Missing item"))
        rule.onNodeWithText("Missing item").performClick()
        assertEquals("complaint-1", openedComplaint)
    }

    @Test
    fun the_retired_in_review_status_is_not_offered_as_a_filter() {
        show(FakeComplaintRepository())

        rule.waitUntilExactlyOneExists(hasText("In progress"))
        rule.onNodeWithText("In review").assertDoesNotExist()
    }

    @Test
    fun a_failed_page_shows_the_mapped_copy() {
        val repo = FakeComplaintRepository().apply {
            listResult = Outcome.Failure(AppError.Network)
        }
        show(repo)

        rule.waitUntilExactlyOneExists(
            hasText("Couldn't reach the store — check your connection and retry."),
        )
    }
}
