package com.vedant.admin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.feature.catalogue.categories.CategoriesScreen
import com.vedant.admin.feature.catalogue.categories.CategoriesViewModel
import com.vedant.admin.feature.catalogue.hub.CatalogueHubScreen
import com.vedant.admin.feature.catalogue.hub.CatalogueHubViewModel
import com.vedant.admin.feature.catalogue.offers.OffersScreen
import com.vedant.admin.feature.catalogue.offers.OffersViewModel
import com.vedant.admin.feature.catalogue.weights.WeightsScreen
import com.vedant.admin.feature.catalogue.weights.WeightsViewModel
import com.vedant.admin.feature.slots.SlotsScreen
import com.vedant.admin.feature.slots.SlotsViewModel
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.FakeOfferRepository
import com.vedant.admin.testing.FakeProductRepository
import com.vedant.admin.testing.FakeSlotRepository
import com.vedant.admin.testing.FakeWeightRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testProduct
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Rule
import org.junit.Test

/**
 * DEFECT-1 — offline gating on every mutation surface.
 *
 * A mutation that cannot reach the server is **disabled**, never queued or faked
 * (T24). These tests prove the two halves of that contract per surface:
 *  - **offline**: the action control is not enabled *and* an
 *    "Unavailable offline…" hint is on screen, so a disabled control never reads
 *    as "broken";
 *  - **online**: the action control is enabled once its own precondition is met
 *    (gating is additive — it must not couple to loading/saving state).
 *
 * The online state is the same `LocalOnlineStatus` CompositionLocal the app
 * provides at the top; here it is overridden directly so both branches are
 * exercised without a device, credentials or a live backend (**BLOCKED (R1)**).
 */
@OptIn(ExperimentalTestApi::class)
class OfflineGatingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val offlineHint = "Reconnect to save — changes are not queued while offline."

    // --- Catalogue hub: the inline availability switch (renders without a sheet) ---

    private fun showHub(online: Boolean) {
        val products = FakeProductRepository().apply {
            listResult = Outcome.Success(listOf(testProduct()))
        }
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOnlineStatus provides online) {
                    CatalogueHubScreen(
                        factory = fakeViewModelFactory {
                            CatalogueHubViewModel(products, FakeCategoryRepository())
                        },
                        onOpenProduct = {},
                        onOpenCategories = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun hub_availability_switch_is_disabled_with_a_hint_when_offline() {
        showHub(online = false)
        rule.waitUntilExactlyOneExists(hasText("Tomatoes"))
        rule.onNode(isToggleable()).assertIsNotEnabled()
        rule.onNodeWithText("Availability can't be changed offline.").assertExists()
    }

    @Test
    fun hub_availability_switch_is_enabled_when_online() {
        showHub(online = true)
        rule.waitUntilExactlyOneExists(hasText("Tomatoes"))
        rule.onNode(isToggleable()).assertIsEnabled()
    }

    // --- Categories: create/update form ---

    private fun showCategories(online: Boolean) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOnlineStatus provides online) {
                    CategoriesScreen(
                        factory = fakeViewModelFactory {
                            CategoriesViewModel(FakeCategoryRepository())
                        },
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun categories_create_button_is_disabled_with_a_hint_when_offline() {
        showCategories(online = false)
        rule.onNodeWithContentDescription("Add category").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create category"))
        // Fill the form so the button's own precondition passes; only the offline
        // gate may keep it disabled.
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Fruit")
        rule.onNode(hasText("Create category") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText(offlineHint).assertExists()
    }

    @Test
    fun categories_create_button_is_enabled_when_online() {
        showCategories(online = true)
        rule.onNodeWithContentDescription("Add category").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create category"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Fruit")
        rule.onNode(hasText("Create category") and hasClickAction()).assertIsEnabled()
    }

    // --- Offers: create/update form ---

    private fun showOffers(online: Boolean) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOnlineStatus provides online) {
                    OffersScreen(
                        factory = fakeViewModelFactory {
                            OffersViewModel(FakeOfferRepository())
                        },
                        productId = "p1",
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun offers_create_button_is_disabled_with_a_hint_when_offline() {
        showOffers(online = false)
        rule.onNodeWithContentDescription("Add offer").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create offer"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Diwali 10%")
        rule.onNode(hasText("Create offer") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText(offlineHint).assertExists()
    }

    @Test
    fun offers_create_button_is_enabled_when_online() {
        showOffers(online = true)
        rule.onNodeWithContentDescription("Add offer").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create offer"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Diwali 10%")
        rule.onNode(hasText("Create offer") and hasClickAction()).assertIsEnabled()
    }

    // --- Weights: save form ---

    private fun showWeights(online: Boolean) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOnlineStatus provides online) {
                    WeightsScreen(
                        factory = fakeViewModelFactory {
                            WeightsViewModel(FakeWeightRepository())
                        },
                        productId = "p1",
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun weights_save_button_is_disabled_with_a_hint_when_offline() {
        showWeights(online = false)
        rule.onNodeWithContentDescription("Add variant").performClick()
        rule.waitUntilExactlyOneExists(hasText("Add variant"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("500 g")
        rule.onNode(hasText("Add variant") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText(offlineHint).assertExists()
    }

    @Test
    fun weights_save_button_is_enabled_when_online() {
        showWeights(online = true)
        rule.onNodeWithContentDescription("Add variant").performClick()
        rule.waitUntilExactlyOneExists(hasText("Add variant"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("500 g")
        rule.onNode(hasText("Add variant") and hasClickAction()).assertIsEnabled()
    }

    // --- Slots: save form ---

    private fun showSlots(online: Boolean) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOnlineStatus provides online) {
                    SlotsScreen(
                        factory = fakeViewModelFactory {
                            SlotsViewModel(FakeSlotRepository())
                        },
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun slots_create_button_is_disabled_with_a_hint_when_offline() {
        showSlots(online = false)
        rule.onNodeWithContentDescription("Add slot").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create slot"))
        // Capacity is the button's precondition field; it is the 4th text field
        // (name, start time, end time, capacity) in the sheet.
        rule.onAllNodes(hasSetTextAction())[3].performTextInput("20")
        rule.onNode(hasText("Create slot") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText(offlineHint).assertExists()
    }

    @Test
    fun slots_create_button_is_enabled_when_online() {
        showSlots(online = true)
        rule.onNodeWithContentDescription("Add slot").performClick()
        rule.waitUntilExactlyOneExists(hasText("Create slot"))
        rule.onAllNodes(hasSetTextAction())[3].performTextInput("20")
        rule.onNode(hasText("Create slot") and hasClickAction()).assertIsEnabled()
    }
}
