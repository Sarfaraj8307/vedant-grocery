package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.dto.ProfileDto
import com.vedant.admin.testing.FakeAuthRepository
import com.vedant.admin.testing.FakeSettingsRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T23 acceptance for the profile repository, proven against fakes — live access
 * is **[BLOCKED]** (no admin creds).
 *
 * The load-bearing rule: **the role is the server's `is_admin()` answer**, never a
 * cached local value. A failed `is_admin()` is propagated rather than defaulted to
 * "not an administrator", which would be a wrong answer presented as truth.
 */
class ProfileRepositoryImplTest {

    private val remote = FakeSettingsRemoteSource()
    private val auth = FakeAuthRepository()
    private val repository = ProfileRepositoryImpl(remote = remote, auth = auth)

    @Test
    fun `the role is the server's is_admin answer`() = runTest {
        auth.adminResult = Outcome.Success(false)

        val result = repository.getProfile()

        assertTrue(result is Outcome.Success)
        val profile = (result as Outcome.Success).value
        assertFalse(profile.isAdmin)
        assertEquals("Not an administrator", profile.roleLabel)
        assertEquals("is_admin() must be consulted", 1, auth.isAdminCalls)
    }

    @Test
    fun `an admin is reported as such`() = runTest {
        auth.adminResult = Outcome.Success(true)

        val profile = (repository.getProfile() as Outcome.Success).value

        assertTrue(profile.isAdmin)
        assertEquals("Administrator", profile.roleLabel)
    }

    @Test
    fun `no session is a SessionExpired failure and is_admin is not called`() = runTest {
        auth.userId = null

        val result = repository.getProfile()

        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.SessionExpired, (result as Outcome.Failure).error)
        assertEquals(0, auth.isAdminCalls)
    }

    @Test
    fun `a failed is_admin is propagated, not defaulted to not-an-admin`() = runTest {
        auth.adminResult = Outcome.Failure(AppError.Network)

        val result = repository.getProfile()

        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.Network, (result as Outcome.Failure).error)
    }

    @Test
    fun `a missing profile row still yields an identity from the session`() = runTest {
        remote.profile = null

        val profile = (repository.getProfile() as Outcome.Success).value

        assertEquals("u1", profile.userId)
        assertEquals("owner@vedant.test", profile.email)
    }

    @Test
    fun `the stored role column is carried but is not the gate`() = runTest {
        remote.profile = ProfileDto(
            id = "u1",
            username = "owner",
            email = "owner@vedant.test",
            role = "admin",
        )
        auth.adminResult = Outcome.Success(false)

        val profile = (repository.getProfile() as Outcome.Success).value

        assertEquals("the raw column is carried for display", "admin", profile.storedRole)
        assertFalse("the stored role must not drive access", profile.isAdmin)
        assertEquals("Not an administrator", profile.roleLabel)
    }
}
