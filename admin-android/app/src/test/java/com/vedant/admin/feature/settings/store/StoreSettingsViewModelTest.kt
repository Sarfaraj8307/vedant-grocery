package com.vedant.admin.feature.settings.store

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.testing.FakeStoreSettingsRepository
import com.vedant.admin.testing.MainDispatcherRule
import com.vedant.admin.testing.testStoreSettings
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T23 acceptance for the store-settings screen. Pins the "the row is the server's"
 * rule, local validation, the `store_name` mismatch flag, and that a rejected save
 * leaves the form exactly as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeStoreSettingsRepository()

    private fun viewModel() = StoreSettingsViewModel(settings)

    @Test
    fun `start loads the row into the form`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        settings.getResult = Outcome.Success(testStoreSettings(storeName = "Vedant Fruit & Vegetables"))
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ContentPhase.Data, state.phase)
        assertEquals("Vedant Fruit & Vegetables", state.form.storeName)
        assertEquals("20.00", state.form.deliveryFee)
        assertFalse(state.isDirty)
    }

    @Test
    fun `the wrong live name is flagged and never rewritten`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            settings.getResult = Outcome.Success(testStoreSettings(storeName = "Fresh Mart"))
            val vm = viewModel()

            vm.start()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.nameMismatch)
            assertEquals("the value is shown as-is", "Fresh Mart", vm.uiState.value.form.storeName)
        }

    @Test
    fun `editing a field marks the form dirty and clears its error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onStoreNameChange("Vedant Fruit & Vegetables")

            assertTrue(vm.uiState.value.isDirty)
        }

    @Test
    fun `an invalid amount is refused without a repository call`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onDeliveryFeeChange("not a number")
            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.errorFor(StoreSettingsField.DELIVERY_FEE))
            assertEquals(0, settings.saveCalls)
        }

    @Test
    fun `a blank store name is refused without a repository call`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onStoreNameChange("   ")
            vm.save()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.errorFor(StoreSettingsField.STORE_NAME))
            assertEquals(0, settings.saveCalls)
        }

    @Test
    fun `a successful save adopts the server's echoed row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            settings.saveResult = Outcome.Success(testStoreSettings(storeName = "Vedant Fruit & Vegetables"))
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onStoreNameChange("Vedant Fruit & Vegetables")
            vm.save()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, settings.saveCalls)
            assertEquals("Vedant Fruit & Vegetables", state.form.storeName)
            assertEquals("Vedant Fruit & Vegetables", state.saved?.storeName)
            assertFalse("the form now matches the server", state.isDirty)
        }

    @Test
    fun `a failed save keeps the form and surfaces the error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            settings.saveResult = Outcome.Failure(AppError.Unauthorized)
            val vm = viewModel()
            vm.start()
            advanceUntilIdle()

            vm.onStoreNameChange("Vedant Fruit & Vegetables")
            vm.save()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("the edit is not lost", "Vedant Fruit & Vegetables", state.form.storeName)
            assertEquals(AppError.Unauthorized, state.saveError)
        }

    @Test
    fun `a save writes the parsed money values`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.onDeliveryFeeChange("25.50")
        vm.onMinOrderAmountChange("150")
        vm.save()
        advanceUntilIdle()

        val draft = settings.lastDraft
        assertNotNull(draft)
        assertEquals(Money(2_550), draft?.deliveryFee)
        assertEquals(Money(15_000), draft?.minOrderAmount)
    }

    @Test
    fun `a negative amount is rejected`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.onFreeDeliveryThresholdChange("-5")
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorFor(StoreSettingsField.FREE_DELIVERY_THRESHOLD))
        assertEquals(0, settings.saveCalls)
    }

    @Test
    fun `a malformed phone number is rejected`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.start()
        advanceUntilIdle()

        vm.onPhoneChange("call me")
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorFor(StoreSettingsField.PHONE))
        assertEquals(0, settings.saveCalls)
    }

    @Test
    fun `a failed load is the Error phase`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        settings.getResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()

        vm.start()
        advanceUntilIdle()

        assertEquals(ContentPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.Network, vm.uiState.value.loadError)
    }
}
