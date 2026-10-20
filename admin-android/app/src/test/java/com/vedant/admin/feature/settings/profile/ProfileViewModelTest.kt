package com.vedant.admin.feature.settings.profile

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeAuthRepository
import com.vedant.admin.testing.FakeProfileRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testOperatorProfile
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T23 acceptance for the profile screen. Pins that the shown role is the
 * **server's** `is_admin()` answer (never a locally cached role), that an expired
 * session is surfaced plainly, and that sign-out is delegated to the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val auth = FakeAuthRepository()

    private fun viewModel() = ProfileViewModel(profiles, auth)

    @Test
    fun `start shows the server-resolved administrator role`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            profiles.getResult = Outcome.Success(testOperatorProfile(isAdmin = true))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ContentPhase.Data, state.phase)
            assertEquals("Administrator", state.profile?.roleLabel)
            assertTrue(state.profile?.isAdmin == true)
        }

    @Test
    fun `a non-admin is shown as not an administrator`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            profiles.getResult = Outcome.Success(testOperatorProfile(isAdmin = false))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertEquals("Not an administrator", vm.uiState.value.profile?.roleLabel)
            assertFalse(vm.uiState.value.profile?.isAdmin ?: true)
        }

    @Test
    fun `a failed load is the Error phase`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        profiles.getResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(ContentPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.Network, vm.uiState.value.loadError)
    }

    @Test
    fun `an expired session is flagged plainly`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        profiles.getResult = Outcome.Failure(AppError.SessionExpired)
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sessionExpired)
    }

    @Test
    fun `start is idempotent`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()

        vm.start()
        vm.start()
        advanceUntilIdle()

        assertEquals(1, profiles.getCalls)
    }

    @Test
    fun `reload refetches the profile`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.reload()
        advanceUntilIdle()

        assertEquals(2, profiles.getCalls)
    }

    @Test
    fun `sign out delegates to the session`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
        assertFalse(vm.uiState.value.isSigningOut)
        assertNull(vm.uiState.value.signOutError)
    }

    @Test
    fun `a failed sign-out surfaces the error and is not stuck in flight`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            auth.signOutResult = Outcome.Failure(AppError.Network)
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.signOut()
            advanceUntilIdle()

            assertEquals(AppError.Network, vm.uiState.value.signOutError)
            assertFalse("the button must not stay in flight", vm.uiState.value.isSigningOut)
        }
}
