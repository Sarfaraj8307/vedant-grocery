package com.vedant.admin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.feature.orders.detail.OrderDetailScreen
import com.vedant.admin.feature.orders.detail.OrderDetailViewModel
import com.vedant.admin.testing.FakeOrderRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testOrder
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S6 — Order detail + status workflow. Driven by a fake [FakeOrderRepository];
 * no live RPC is made (that path is **BLOCKED (R1)**).
 *
 * This is where the two non-negotiables are most visible, so they are asserted
 * directly: `final_total` is rendered **verbatim from the server row**, and a
 * rejected status change **does not move the UI** — the rendered status stays and
 * the mapped copy appears in the pinned banner.
 */
@OptIn(ExperimentalTestApi::class)
class OrderDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var openedComplaint: String? = null

    private fun show(repo: FakeOrderRepository) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                OrderDetailScreen(
                    factory = fakeViewModelFactory { OrderDetailViewModel(repo) },
                    orderId = "o1",
                    onBack = {},
                    onOpenComplaint = { openedComplaint = it },
                )
            }
        }
    }

    @Test
    fun the_final_total_is_rendered_verbatim_from_the_server_row() {
        // A value the client could not derive from the line items (8_000) alone —
        // proving it is the server's number that is shown, not a local sum.
        val repo = FakeOrderRepository().apply {
            getOrderResult = Outcome.Success(testOrder(finalTotal = Money(17_750)))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Final total"))
        rule.onNodeWithText("₹177.50").assertExists()
        rule.onNodeWithText("Charged amount, as confirmed by the server.").assertExists()
    }

    @Test
    fun only_the_offered_transitions_are_shown() {
        val repo = FakeOrderRepository().apply {
            getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Accept order"))
        // PENDING offers CONFIRMED and CANCELLED only.
        rule.onNodeWithText("Cancel order").assertExists()
        rule.onNodeWithText("Dispatch").assertDoesNotExist()
    }

    @Test
    fun a_rejected_status_change_leaves_the_rendered_status_unchanged() {
        val repo = FakeOrderRepository().apply {
            getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            updateStatusResult = Outcome.Failure(AppError.Unauthorized)
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Accept order"))
        rule.onNodeWithText("Accept order").performClick()

        // The mapped copy is shown, and the status never advanced.
        rule.waitUntilExactlyOneExists(
            hasText("You don't have permission to perform this action."),
        )
        rule.onAllNodesWithText("Order received")[0].assertExists()
        rule.onNodeWithText("Confirmed by store").assertDoesNotExist()
    }

    @Test
    fun a_successful_status_change_renders_the_server_row() {
        val repo = FakeOrderRepository().apply {
            getOrderResult = Outcome.Success(testOrder(status = OrderStatus.PENDING))
            updateStatusResult = Outcome.Success(testOrder(status = OrderStatus.CONFIRMED))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Accept order"))
        rule.onNodeWithText("Accept order").performClick()

        // "Confirmed by store" legitimately appears on TWO nodes once the transition
        // lands: the top bar's subtitle (OrderDetailScreen.kt:141, via
        // Order.statusLabel) and the status badge (OrderDetailScreen.kt:289). So
        // `waitUntilExactlyOneExists` can never be satisfied here — it demands
        // exactly one match and there are two. Wait for the state to settle on any
        // match, then assert the previous status is gone. (Asserting "at least one"
        // rather than exactly two, so a future chrome change does not re-break it.)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Confirmed by store", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Confirmed by store", substring = true)[0].assertExists()
        rule.onNodeWithText("Order received", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_terminal_order_offers_no_further_actions() {
        val repo = FakeOrderRepository().apply {
            getOrderResult = Outcome.Success(testOrder(status = OrderStatus.DELIVERED))
        }
        show(repo)

        rule.waitUntilExactlyOneExists(
            hasText("No further actions — this order is closed."),
        )
    }

    @Test
    fun the_receipt_renders_the_line_items_and_their_totals() {
        val repo = FakeOrderRepository()
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Tomatoes"))
        // The line renders as "<weight> · <qty> × <price>" joined with " · "
        // (OrderDetailScreen.kt:370-374), so the whole node text is
        // "1 kg · 2 × ₹40.00" — an exact match on the fragment can never satisfy it.
        rule.onNodeWithText("2 × ₹40.00", substring = true).assertExists()
        rule.onNodeWithText("₹80.00", substring = true).assertExists()
    }

    @Test
    fun the_complaint_link_routes_with_the_order_id() {
        val repo = FakeOrderRepository()
        show(repo)

        rule.waitUntilExactlyOneExists(hasText("Raise a complaint"))
        rule.onNodeWithText("Raise a complaint").performClick()
        assertEquals("o1", openedComplaint)
    }
}
