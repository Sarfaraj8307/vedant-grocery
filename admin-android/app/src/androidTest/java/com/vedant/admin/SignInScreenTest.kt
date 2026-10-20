package com.vedant.admin

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.feature.auth.SignInScreen
import com.vedant.admin.feature.auth.SignInViewModel
import com.vedant.admin.testing.FakeAuthRepository
import com.vedant.admin.testing.fakeViewModelFactory
import com.vedant.admin.ui.theme.VedantAdminTheme
import org.junit.Rule
import org.junit.Test

/**
 * S2 — Sign in (PRD screen list). Instrumented Compose test driven entirely by a
 * fake [FakeAuthRepository]; no live sign-in is attempted (that path is
 * **BLOCKED (R1)** — no admin credentials).
 *
 * The suite proves the form's rendering contract: the fields exist, the submit
 * button is disabled until the form is fillable, a **server** failure shows the
 * mapped copy (never a raw code), and a success clears the password.
 */
class SignInScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val submitButton = hasText("Sign in") and hasClickAction()

    private fun show(auth: FakeAuthRepository) {
        rule.setContent {
            VedantAdminTheme(darkTheme = false) {
                SignInScreen(factory = fakeViewModelFactory { SignInViewModel(auth) })
            }
        }
    }

    private fun typeEmail(value: String) =
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(value)

    private fun typePassword(value: String) =
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(value)

    @Test
    fun the_screen_shows_its_heading_and_the_admin_only_note() {
        show(FakeAuthRepository())
        rule.onNodeWithText("Vedant Fruit & Vegetables — store operations").assertExists()
        rule.onNodeWithText("Only store administrators can use this app.").assertExists()
    }

    @Test
    fun the_submit_button_is_disabled_until_both_fields_are_filled() {
        show(FakeAuthRepository())
        rule.onNode(submitButton).assertIsNotEnabled()

        typeEmail("owner@vedant.test")
        rule.onNode(submitButton).assertIsNotEnabled()

        typePassword("secret")
        rule.onNode(submitButton).assertIsEnabled()
    }

    @Test
    fun a_rejected_sign_in_shows_the_mapped_copy_and_never_a_raw_code() {
        val auth = FakeAuthRepository().apply {
            signInResult = Outcome.Failure(AppError.Unauthorized)
        }
        show(auth)

        typeEmail("owner@vedant.test")
        typePassword("wrong")
        rule.onNode(submitButton).performClick()

        rule.onNodeWithText("You don't have permission to perform this action.").assertExists()
        // The button is usable again once the request has resolved.
        rule.onNode(submitButton).assertIsEnabled()
    }

    @Test
    fun a_successful_sign_in_clears_the_password_field() {
        val auth = FakeAuthRepository().apply { signInResult = Outcome.Success(Unit) }
        show(auth)

        typeEmail("owner@vedant.test")
        typePassword("secret")
        rule.onNode(submitButton).performClick()

        // The password is wiped from memory on success, so the button disables.
        rule.onNode(submitButton).assertIsNotEnabled()
    }
}
