package com.vedant.admin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vedant.admin.feature.dashboard.DashboardScreen
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S4 — Dashboard. A stateless screen, so it is driven directly with callbacks.
 * It is only ever reached behind the server-confirmed admin gate; this suite
 * proves the landing screen exposes the operational entry points and routes them.
 */
class DashboardScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var opened: String? = null
    private var signedOut = false

    private fun show(email: String? = "owner@vedant.test") {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                DashboardScreen(
                    email = email,
                    onOpenOrders = { opened = "orders" },
                    onOpenCatalogue = { opened = "catalogue" },
                    onOpenSlots = { opened = "slots" },
                    onOpenSupport = { opened = "support" },
                    onOpenStoreSettings = { opened = "settings" },
                    onOpenProfile = { opened = "profile" },
                    onSignOut = { signedOut = true },
                )
            }
        }
    }

    @Test
    fun the_signed_in_email_is_shown() {
        show("owner@vedant.test")
        // Shown twice by design: the top app bar shows the signed-in identity, and the
        // greeting block repeats it. Assert both occurrences explicitly.
        rule.onAllNodesWithText("owner@vedant.test").assertCountEquals(2)
    }

    @Test
    fun every_operational_entry_point_is_present() {
        show()
        listOf(
            "Manage orders",
            "Catalogue",
            "Delivery slots",
            "Support",
            "Store settings",
            "Profile",
            "Sign out",
        ).forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun manage_orders_routes_to_the_orders_destination() {
        show()
        rule.onNodeWithText("Manage orders").performClick()
        assertEquals("orders", opened)
    }

    @Test
    fun support_routes_to_the_complaints_destination() {
        show()
        rule.onNodeWithText("Support").performClick()
        assertEquals("support", opened)
    }

    @Test
    fun the_sign_out_action_in_the_bar_signs_out() {
        show()
        rule.onNodeWithContentDescription("Sign out").performClick()
        assertTrue(signedOut)
    }
}
