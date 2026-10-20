package com.vedant.admin.core.network

import app.cash.turbine.test
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five explicit connection states (§52).
 *
 * The point of these tests is the case the old boolean could not represent:
 * **a request that fails while the network is present must not read as
 * "offline."** `ConnectionState.Error` carries the server's own reason and
 * `isOffline` is false for it — so the UI can say "we could not reach the
 * store" instead of telling the operator to check a connection that is fine.
 *
 * Every assertion goes through a real collector (turbine's `test`). Reading
 * `state.value` directly would be wrong here: `stateIn(…, Eagerly, …)` computes
 * in a sharing coroutine, and a bare `.value` read observes the initial value
 * without ever scheduling the upstream — so the transition tests must actually
 * collect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionMonitorTest {

    private class FakeConnectivity(online: Boolean = true) : ConnectivityObserver {
        private val flow = MutableStateFlow(online)
        override val isOnline = flow
        fun set(value: Boolean) {
            flow.value = value
        }
    }

    /** Builds a monitor tied to the test's background scope, plus its fake. */
    private fun TestScope.fixture(online: Boolean = true): Pair<ConnectionMonitor, FakeConnectivity> {
        val fake = FakeConnectivity(online)
        return ConnectionMonitor(fake, backgroundScope) to fake
    }

    @Test
    fun `network present and nothing attempted yet is Online`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no network is Offline regardless of the last sync outcome`() = runTest {
        val (monitor, _) = fixture(online = false)
        monitor.state.test {
            assertEquals(ConnectionState.Offline, awaitItem())

            monitor.onSyncSucceeded()
            // Offline must win, so the derived state does not change and no new
            // value is emitted at all: a "successful" sync on a dead network is
            // impossible, and the rows on screen are genuinely cached.
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a safe read in flight is Syncing and then Synced`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())

            monitor.onSyncStarted()
            assertEquals(ConnectionState.Syncing, awaitItem())

            monitor.onSyncSucceeded()
            assertEquals(ConnectionState.Synced, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `losing the network while synced degrades to Offline and recovers`() = runTest {
        val (monitor, fake) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())
            monitor.onSyncSucceeded()
            assertEquals(ConnectionState.Synced, awaitItem())

            // The phone drops off the network mid-session.
            fake.set(false)
            assertEquals(ConnectionState.Offline, awaitItem())

            // Reconnecting resumes from the last known sync outcome.
            fake.set(true)
            assertEquals(ConnectionState.Synced, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a network failure while online is an Error, not Offline`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())

            monitor.onSyncFailed(AppError.Network)
            val state = awaitItem()
            assertTrue(
                "a transport failure must not claim the device is offline",
                state is ConnectionState.Error,
            )
            assertFalse(state.isOffline)
            assertEquals(ErrorCopy.of(AppError.Network), (state as ConnectionState.Error).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a server rejection while online keeps its own reason`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())

            monitor.onSyncFailed(AppError.Unauthorized)
            val state = awaitItem()
            assertTrue(state is ConnectionState.Error)
            // Crucially NOT the offline copy — this is the mislabelling that was fixed.
            assertFalse(state.isOffline)
            assertEquals(
                ErrorCopy.of(AppError.Unauthorized),
                (state as ConnectionState.Error).message,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only Offline permits the saved-data framing`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())

            monitor.onSyncFailed(AppError.Network)
            val state = awaitItem()
            // An Error still blocks mutations, but it is not "showing saved data".
            assertTrue(state.isBlocked)
            assertFalse(state.isOffline)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset forgets the last outcome`() = runTest {
        val (monitor, _) = fixture()
        monitor.state.test {
            assertEquals(ConnectionState.Online, awaitItem())

            monitor.onSyncFailed(AppError.Network)
            assertTrue(awaitItem() is ConnectionState.Error)

            monitor.reset()
            assertEquals(ConnectionState.Online, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
