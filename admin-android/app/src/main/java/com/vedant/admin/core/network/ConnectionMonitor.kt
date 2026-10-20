package com.vedant.admin.core.network

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What happened to the most recent attempt to refresh the cache. */
private sealed interface SyncOutcome {
    /** Nothing has been attempted since sign-in. */
    data object Never : SyncOutcome

    /** A read is in flight. */
    data object InFlight : SyncOutcome

    /** The last read succeeded. */
    data object Succeeded : SyncOutcome

    /**
     * The last read failed. [message] is already operator-facing copy.
     *
     * [wasNetwork] records whether the failure was itself a transport failure.
     * It is kept because the distinction survives into [ConnectionState.Error]:
     * a network error while "online" is transient and worth a retry, whereas a
     * server rejection (e.g. `42501`) is not going to fix itself.
     */
    data class Failed(val message: String, val wasNetwork: Boolean) : SyncOutcome
}

/**
 * Turns raw connectivity plus the outcome of real requests into the
 * [ConnectionState] the UI shows.
 *
 * **Why this exists rather than using [ConnectivityObserver] directly.**
 * `NET_CAPABILITY_INTERNET` is a capability the network *claims*, not a proof
 * that the store's backend is reachable. The old single boolean therefore could
 * not distinguish:
 *
 *  - the phone genuinely has no network (real offline), from
 *  - the phone is on a Wi-Fi that has no internet, or the backend is down
 *    (which rendered as "offline" and told the operator to check a connection
 *    that was fine).
 *
 * By folding in the **actual request outcome**, the two become different
 * states: [ConnectionState.Error] means "you have a network, but we could not
 * reach the store" — which is the case the old code mislabelled.
 *
 * Only **safe reads** report here. A mutation that fails is surfaced as its own
 * error by the screen that attempted it; it must not silently downgrade the
 * whole app to "offline".
 */
class ConnectionMonitor(
    connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val copyFor: (AppError) -> String = ErrorCopy::of,
) {

    private val outcome = MutableStateFlow<SyncOutcome>(SyncOutcome.Never)

    /** The state every screen reads. Never null, always one of the five. */
    val state: StateFlow<ConnectionState> =
        combine(connectivity.isOnline, outcome) { online, sync ->
            derive(online, sync)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = derive(connectivity.isOnline.value, outcome.value),
        )

    /** Called when a safe read starts, so stale rows can be labelled. */
    fun onSyncStarted() {
        if (outcome.value !is SyncOutcome.InFlight) outcome.value = SyncOutcome.InFlight
    }

    /** Called when a safe read completes and the cache is current. */
    fun onSyncSucceeded() {
        outcome.value = SyncOutcome.Succeeded
    }

    /**
     * Called when a safe read fails.
     *
     * [AppError.Network] is treated as a transport failure and keeps the
     * operator on a retry path; anything else is a real server rejection and is
     * shown as [ConnectionState.Error] with its own message, so a `42501` can
     * never masquerade as "you are offline".
     */
    fun onSyncFailed(error: AppError) {
        outcome.value = SyncOutcome.Failed(
            message = copyFor(error),
            wasNetwork = error == AppError.Network,
        )
    }

    /** Forgets the last outcome — used on sign-out so the next session starts clean. */
    fun reset() {
        outcome.value = SyncOutcome.Never
    }

    private fun derive(online: Boolean, sync: SyncOutcome): ConnectionState = when {
        !online -> ConnectionState.Offline
        sync is SyncOutcome.Failed -> ConnectionState.Error(sync.message)
        sync is SyncOutcome.InFlight -> ConnectionState.Syncing
        sync is SyncOutcome.Succeeded -> ConnectionState.Synced
        else -> ConnectionState.Online
    }
}
