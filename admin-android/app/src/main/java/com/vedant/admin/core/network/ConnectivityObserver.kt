package com.vedant.admin.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.vedant.admin.core.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

private const val TAG = "Connectivity"

/**
 * Whether the device currently has a network.
 *
 * An interface so the offline banner can be driven by a fake in tests. The value
 * is deliberately **coarse** — "is there a usable network" — because it only
 * drives a banner and the disabling of mutations; the repositories still treat a
 * failed request as the authority on what actually happened.
 */
interface ConnectivityObserver {

    /** `true` when a network with internet capability is available. */
    val isOnline: StateFlow<Boolean>
}

/**
 * The real [ConnectivityObserver], backed by `ConnectivityManager`'s default
 * network callback.
 *
 * The flow is app-scoped ([scope]) and `Eagerly` started so the first collector
 * never sees a stale initial value. `NET_CAPABILITY_INTERNET` is used (not
 * `VALIDATED`) so a captive portal or a freshly-joined Wi-Fi does not read as
 * "offline" — a genuinely broken connection still surfaces as a failed request,
 * which is what actually drives the cache fallback.
 */
class AndroidConnectivityObserver(
    context: Context,
    scope: CoroutineScope,
) : ConnectivityObserver {

    private val manager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override val isOnline: StateFlow<Boolean> =
        callbackFlow {
            trySend(manager.isCurrentlyOnline())
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(manager.isCurrentlyOnline())
                }

                override fun onLost(network: Network) {
                    trySend(manager.isCurrentlyOnline())
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    trySend(manager.isCurrentlyOnline())
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { manager?.registerNetworkCallback(request, callback) }
                .onFailure { AppLog.w(TAG, "could not register the network callback", it) }
            awaitClose {
                runCatching { manager?.unregisterNetworkCallback(callback) }
            }
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, manager.isCurrentlyOnline())
}

/** A coarse "is there a network" check. */
private fun ConnectivityManager?.isCurrentlyOnline(): Boolean {
    val manager = this ?: return true // no manager available: do not claim offline
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
