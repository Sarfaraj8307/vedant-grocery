package com.vedant.admin.data.repository

import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.testing.FakeAppearancePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The repository over the appearance preferences (§8): the flow is seeded from
 * storage (so the first frame is right) and writes land in storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceRepositoryImplTest {

    @Test
    fun `the flow is seeded synchronously from storage`() = runTest {
        val preferences = FakeAppearancePreferences(initial = AppearanceMode.DARK)

        val repository = AppearanceRepositoryImpl(preferences)

        assertEquals(AppearanceMode.DARK, repository.mode.value)
    }

    @Test
    fun `a write reaches storage and the flow`() = runTest {
        val preferences = FakeAppearancePreferences(initial = AppearanceMode.SYSTEM_DEFAULT)
        val repository = AppearanceRepositoryImpl(preferences)

        repository.setMode(AppearanceMode.LIGHT)

        assertEquals(AppearanceMode.LIGHT, preferences.stored)
        assertEquals(AppearanceMode.LIGHT, repository.mode.value)
        assertEquals(1, preferences.setCalls)
    }

    @Test
    fun `the wire value survives the round trip through storage`() = runTest {
        // What a real restart looks like to the repository: new prefs, new repo,
        // value read back from what the previous instance wrote.
        val preferences = FakeAppearancePreferences()
        val first = AppearanceRepositoryImpl(preferences)
        first.setMode(AppearanceMode.DARK)

        val second = AppearanceRepositoryImpl(preferences)

        assertEquals(AppearanceMode.DARK, second.mode.value)
    }
}
