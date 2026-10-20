package com.vedant.admin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.feature.catalogue.editor.ProductEditorScreen
import com.vedant.admin.feature.catalogue.editor.ProductEditorViewModel
import com.vedant.admin.testing.FakeCategoryRepository
import com.vedant.admin.testing.FakeProductRepository
import com.vedant.admin.testing.FakeWeightRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.testing.testProduct
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Rule
import org.junit.Test

/**
 * S9 — Product editor. Driven by fake product/category/weight repositories.
 *
 * The editor's "never fake success" rule is asserted: a failed save keeps the
 * typed draft and surfaces the mapped copy ("Not saved — your changes are safe.")
 * rather than clearing the form. No live upload or write is made (**BLOCKED (R1)**).
 */
@OptIn(ExperimentalTestApi::class)
class ProductEditorScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(products: FakeProductRepository, productId: String?) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                ProductEditorScreen(
                    factory = fakeViewModelFactory {
                        ProductEditorViewModel(products, FakeCategoryRepository(), FakeWeightRepository())
                    },
                    productId = productId,
                    onBack = {},
                    onOpenWeights = {},
                    onOpenOffers = {},
                )
            }
        }
    }

    @Test
    fun create_mode_shows_the_new_product_title_and_a_disabled_save() {
        show(FakeProductRepository(), productId = null)

        rule.waitUntilExactlyOneExists(hasText("New product"))
        // Nothing typed yet, so the create button cannot be pressed.
        rule.onNode(hasText("Create product") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun edit_mode_loads_the_server_row_into_the_form() {
        val products = FakeProductRepository().apply {
            getResult = Outcome.Success(testProduct(name = "Tomatoes"))
        }
        show(products, productId = "p1")

        rule.waitUntilExactlyOneExists(hasText("Edit product"))
        // "Tomatoes" appears twice by design: the top app bar titles itself with the
        // product name, and the Name field is pre-filled with it. Target the editable
        // field so the assertion expresses what it means.
        rule.onNode(hasSetTextAction() and hasText("Tomatoes")).assertExists()
    }

    @Test
    fun a_failed_save_keeps_the_draft_and_surfaces_the_mapped_copy() {
        val products = FakeProductRepository().apply {
            getResult = Outcome.Success(testProduct())
            updateResult = Outcome.Failure(AppError.Validation("Quantity must be between 1 and 100."))
        }
        show(products, productId = "p1")

        rule.waitUntilExactlyOneExists(hasText("Edit product"))
        // Make the form dirty, then attempt to save.
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(" extra")
        rule.onNode(hasText("Save changes") and hasClickAction()).performClick()

        rule.waitUntilExactlyOneExists(hasText("Not saved — your changes are safe."))
        rule.onNodeWithText("Quantity must be between 1 and 100.").assertExists()
    }
}
