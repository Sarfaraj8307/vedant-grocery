package com.vedant.admin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.feature.catalogue.hub.CatalogueHubScreen
import com.vedant.admin.feature.catalogue.hub.CatalogueHubViewModel
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.FakeProductRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testProduct
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S6 (catalogue hub). Driven by fake product + category repositories.
 *
 * The hub is where an availability toggle lives, and that toggle only moves once
 * the server confirms the new row — so the fake returns the server row and the
 * rendered price/badge follow it. No live RPC is made (**BLOCKED (R1)**).
 */
@OptIn(ExperimentalTestApi::class)
class CatalogueHubScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var openedProduct: String? = null

    private fun show(products: FakeProductRepository) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                CatalogueHubScreen(
                    factory = fakeViewModelFactory {
                        CatalogueHubViewModel(products, FakeCategoryRepository())
                    },
                    onOpenProduct = { openedProduct = it },
                    onOpenCategories = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun an_empty_catalogue_shows_the_empty_state() {
        val products = FakeProductRepository().apply { listResult = Outcome.Success(emptyList()) }
        show(products)

        rule.waitUntilExactlyOneExists(hasText("No products yet"))
        rule.onNodeWithText("Add your first product to start selling.").assertExists()
    }

    @Test
    fun a_loaded_product_renders_its_name_category_and_price() {
        val products = FakeProductRepository().apply {
            listResult = Outcome.Success(listOf(testProduct()))
        }
        show(products)

        rule.waitUntilExactlyOneExists(hasText("Tomatoes"))
        rule.onNodeWithText("Vegetables · per kg").assertExists()
        rule.onNodeWithText("₹40.00").assertExists()
    }

    @Test
    fun tapping_a_product_opens_its_editor() {
        val products = FakeProductRepository().apply {
            listResult = Outcome.Success(listOf(testProduct(id = "p1")))
        }
        show(products)

        rule.waitUntilExactlyOneExists(hasText("Tomatoes"))
        rule.onNodeWithText("Tomatoes").performClick()
        assertEquals("p1", openedProduct)
    }

    @Test
    fun a_failed_page_shows_the_mapped_copy() {
        val products = FakeProductRepository().apply {
            listResult = Outcome.Failure(AppError.Network)
        }
        show(products)

        rule.waitUntilExactlyOneExists(
            hasText("Couldn't reach the store — check your connection and retry."),
        )
    }
}
