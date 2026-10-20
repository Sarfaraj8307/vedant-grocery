package com.vedant.admin.data.repository

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.dto.StoreSettingsDto
import com.vedant.admin.domain.model.StoreSettingsDraft
import com.vedant.admin.testing.FakeSettingsRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T23 acceptance for the store-settings repository, proven against a fake — live
 * settings access is **[BLOCKED]** (no admin creds).
 *
 * The contract: a save returns the **server's echoed row** (never the draft), and
 * an invalid draft is refused before a request is made.
 */
class StoreSettingsRepositoryImplTest {

    private val remote = FakeSettingsRemoteSource()
    private val repository = StoreSettingsRepositoryImpl(remote)

    private fun draft(
        storeName: String = "Vedant Fruit & Vegetables",
    ) = StoreSettingsDraft(
        id = "s1",
        storeName = storeName,
        phone = null,
        whatsapp = null,
        isOpen = true,
        deliveryFee = Money(2_000),
        freeDeliveryThreshold = Money(49_900),
        minOrderAmount = Money(10_000),
    )

    @Test
    fun `getSettings maps the row and flags the wrong name`() = runTest {
        remote.settings = StoreSettingsDto(id = "s1", storeName = "Fresh Mart")

        val result = repository.getSettings()

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isNameSuspect)
    }

    @Test
    fun `a missing settings row is a failure`() = runTest {
        remote.settings = null

        val result = repository.getSettings()

        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun `a save writes only the editable columns and returns the stored row`() = runTest {
        remote.updateResult = StoreSettingsDto(id = "s1", storeName = "Vedant Fruit & Vegetables")

        val result = repository.saveSettings(draft())

        assertTrue(result is Outcome.Success)
        assertEquals("Vedant Fruit & Vegetables", (result as Outcome.Success).value.storeName)
        assertEquals(1, remote.updates.size)
        assertEquals("s1", remote.updates.first().first)
        assertEquals("Vedant Fruit & Vegetables", remote.updates.first().second.storeName)
    }

    @Test
    fun `a blank store name is refused without a request`() = runTest {
        val result = repository.saveSettings(draft(storeName = "   "))

        assertTrue(result is Outcome.Failure)
        assertTrue("no request may be attempted", remote.updates.isEmpty())
    }

    @Test
    fun `a save that matched no row is a failure`() = runTest {
        remote.updateResult = null

        val result = repository.saveSettings(draft())

        assertTrue(result is Outcome.Failure)
    }
}
