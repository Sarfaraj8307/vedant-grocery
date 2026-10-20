package com.vedant.admin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.feature.orders.list.OrdersListScreen
import com.vedant.admin.feature.orders.list.OrdersListViewModel
import com.vedant.admin.testing.FakeOrderRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testSummary
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S5 — Orders list. Driven by a fake [FakeOrderRepository]; no live RPC is made.
 *
 * The list is the operator's default screen, so its four explicit content phases
 * (Loading / Data / Empty / Error) and its row rendering are the contract under
 * test. Filtering and paging are server-side, so only the *rendered* result is
 * asserted here (the paging logic is covered by the JVM `OrdersListViewModelTest`).
 */
@OptIn(ExperimentalTestApi::class)
class OrdersListScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var openedOrder: String? = null

    private fun show(repo: FakeOrderRepository) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                OrdersListScreen(
                    factory = fakeViewModelFactory { OrdersListViewModel(repo) },
                    initialStatus = null,
                    onOpenOrder = { openedOrder = it },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun an_empty_page_shows_the_empty_state() {
        val repo = FakeOrderRepository().apply { listResult = Outcome.Success(emptyList()) }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("No orders yet"))
        rule.onNodeWithText("New orders appear here as customers place them.").assertExists()
    }

    @Test
    fun a_loaded_page_renders_a_scannable_row() {
        val repo = FakeOrderRepository().apply {
            listResult = Outcome.Success(
                listOf(testSummary(id = "o1", status = OrderStatus.PENDING)),
            )
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("#O1"))
        rule.onNodeWithText("Asha").assertExists()
        rule.onNodeWithText("1 item").assertExists()
        // final_total is the server's value, rendered verbatim.
        rule.onNodeWithText("₹152.00").assertExists()
    }

    @Test
    fun tapping_a_row_opens_that_order() {
        val repo = FakeOrderRepository().apply {
            listResult = Outcome.Success(listOf(testSummary(id = "o1")))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("#O1"))
        rule.onNodeWithText("#O1").performClick()
        assertEquals("o1", openedOrder)
    }

    @Test
    fun a_failed_first_page_shows_the_mapped_copy_and_a_retry() {
        val repo = FakeOrderRepository().apply {
            listResult = Outcome.Failure(AppError.Network)
        }
        show(repo)

        rule.waitUntilExactlyOneExists(
            hasText("Couldn't reach the store — check your connection and retry."),
        )
        rule.onNodeWithText("Try again").assertExists()
    }
}
