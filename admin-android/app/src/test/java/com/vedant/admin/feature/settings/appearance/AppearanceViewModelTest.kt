package com.vedant.admin.feature.settings.appearance

import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.testing.FakeAppearanceRepository
import com.vedant.admin.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The appearance ViewModel (§8): it renders the persisted choice and writes a
 * new one through to the repository — it never keeps a private copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the initial state is the persisted choice`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeAppearanceRepository(initial = AppearanceMode.DARK)

            val vm = AppearanceViewModel(repository)
            advanceUntilIdle()

            assertEquals(AppearanceMode.DARK, vm.uiState.value.mode)
        }

    @Test
    fun `selecting a mode persists it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeAppearanceRepository(initial = AppearanceMode.SYSTEM_DEFAULT)
            val vm = AppearanceViewModel(repository)

            vm.select(AppearanceMode.LIGHT)
            advanceUntilIdle()

            assertEquals(AppearanceMode.LIGHT, vm.uiState.value.mode)
            assertEquals(AppearanceMode.LIGHT, repository.mode.value)
            assertEquals(1, repository.setCalls)
        }

    @Test
    fun `re-selecting the current mode does not write`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeAppearanceRepository(initial = AppearanceMode.LIGHT)
            val vm = AppearanceViewModel(repository)

            vm.select(AppearanceMode.LIGHT)
            advanceUntilIdle()

            assertEquals(0, repository.setCalls)
        }

    @Test
    fun `a change written elsewhere is reflected in the state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repository = FakeAppearanceRepository(initial = AppearanceMode.SYSTEM_DEFAULT)
            val vm = AppearanceViewModel(repository)

            repository.setMode(AppearanceMode.DARK)
            advanceUntilIdle()

            assertEquals(AppearanceMode.DARK, vm.uiState.value.mode)
        }
}
