package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.data.session.SessionStore
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.ui.navigation.SessionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The auth gate is the security boundary of the whole app: if it can be made to
 * report an admin when the server said no, every screen behind it is exposed.
 * These tests drive [AuthRepositoryImpl] with fakes so the server's answer is the
 * only thing that decides.
 */
class AuthRepositoryImplTest {

    private class FakeSessionStore(
        initialUserId: String? = "user-1",
        private val email: String? = "owner@example.com",
    ) : SessionStore {

        private var userId: String? = initialUserId
        private val _state = MutableStateFlow<SessionState>(
            if (initialUserId != null) {
                SessionState.SignedIn(initialUserId, email)
            } else {
                SessionState.SignedOut
            },
        )

        var signOutCalls: Int = 0
        var signInCalls: Int = 0

        override val sessionState: StateFlow<SessionState> = _state

        override fun currentUserId(): String? = userId
        override fun currentEmail(): String? = email
        override fun currentAccessToken(): String? = "fake-access-token"

        override suspend fun signIn(email: String, password: String) {
            signInCalls++
            userId = "user-1"
            _state.value = SessionState.SignedIn("user-1", email)
        }

        override suspend fun signOut() {
            signOutCalls++
            userId = null
            _state.value = SessionState.SignedOut
        }

        override suspend fun loadFromStorage(): Boolean = userId != null
    }

    private class FakeAdminRpc(private val admin: Boolean) : AdminRpc {
        var calls: Int = 0
        override suspend fun isAdmin(): Boolean {
            calls++
            return admin
        }

        // Phase-2 order mutations and Phase-3 slot RPCs are not exercised by these auth tests.
        override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) = Unit
        override suspend fun rejectOrderItem(itemId: String, reason: String) = Unit
        override suspend fun restoreOrderItem(itemId: String) = Unit
        override suspend fun ensureDeliverySlots(from: String, days: Int) = Unit
        override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> = emptyList()
        override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> = emptyList()
    }

    // --- restoreSession -----------------------------------------------------

    @Test
    fun `restoreSession returns a non-admin identity when the server says not admin`() = runTest {
        val repository = AuthRepositoryImpl(FakeSessionStore(), FakeAdminRpc(admin = false))

        val outcome = repository.restoreSession()

        assertTrue("expected Success, was $outcome", outcome is Outcome.Success)
        val identity = (outcome as Outcome.Success).value
        assertFalse(identity.isAdmin)
        assertEquals("user-1", identity.userId)
    }

    @Test
    fun `a restored non-admin session resolves the gate to NotAuthorized`() = runTest {
        val repository = AuthRepositoryImpl(FakeSessionStore(), FakeAdminRpc(admin = false))

        val identity = (repository.restoreSession() as Outcome.Success).value
        val gate = SessionViewModel.resolve(
            SessionState.SignedIn(identity.userId, identity.email),
            identity.isAdmin,
        )

        assertEquals(SessionState.NotAuthorized, gate)
    }

    @Test
    fun `restoreSession returns an admin identity when the server says admin`() = runTest {
        val repository = AuthRepositoryImpl(FakeSessionStore(), FakeAdminRpc(admin = true))

        val identity = (repository.restoreSession() as Outcome.Success).value

        assertTrue(identity.isAdmin)
        assertEquals(
            SessionState.SignedIn(identity.userId, identity.email),
            SessionViewModel.resolve(SessionState.SignedIn(identity.userId, identity.email), true),
        )
    }

    @Test
    fun `restoreSession without a session fails as SessionExpired`() = runTest {
        val repository = AuthRepositoryImpl(
            FakeSessionStore(initialUserId = null),
            FakeAdminRpc(admin = true),
        )

        val outcome = repository.restoreSession()

        assertTrue("expected Failure, was $outcome", outcome is Outcome.Failure)
        assertEquals(AppError.SessionExpired, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `restoreSession asks the server exactly once for the admin decision`() = runTest {
        val adminRpc = FakeAdminRpc(admin = true)
        val repository = AuthRepositoryImpl(FakeSessionStore(), adminRpc)

        repository.restoreSession()

        assertEquals(1, adminRpc.calls)
    }

    // --- signOut / signIn ---------------------------------------------------

    @Test
    fun `signOut clears the session state`() = runTest {
        val store = FakeSessionStore()
        val repository = AuthRepositoryImpl(store, FakeAdminRpc(admin = true))

        val outcome = repository.signOut()

        assertTrue(outcome is Outcome.Success)
        assertEquals(1, store.signOutCalls)
        assertEquals(SessionState.SignedOut, store.sessionState.value)
    }

    @Test
    fun `signIn delegates to the session store`() = runTest {
        val store = FakeSessionStore(initialUserId = null)
        val repository = AuthRepositoryImpl(store, FakeAdminRpc(admin = true))

        val outcome = repository.signIn("  owner@example.com ", "secret")

        assertTrue(outcome is Outcome.Success)
        assertEquals(1, store.signInCalls)
    }

    @Test
    fun `isAdmin surfaces the server answer`() = runTest {
        val repository = AuthRepositoryImpl(FakeSessionStore(), FakeAdminRpc(admin = true))
        assertEquals(true, (repository.isAdmin() as Outcome.Success).value)

        val denied = AuthRepositoryImpl(FakeSessionStore(), FakeAdminRpc(admin = false))
        assertEquals(false, (denied.isAdmin() as Outcome.Success).value)
    }
}
