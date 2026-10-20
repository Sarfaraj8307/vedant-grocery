package com.vedant.admin.core.network

/**
 * The explicit connection state the operator is actually in.
 *
 * The brief (§52) requires five distinct states and, just as importantly,
 * forbids **confusing cached data with actual offline mode**. A single boolean
 * cannot express that: with `isOnline: Boolean` a dead backend and a genuinely
 * disconnected phone look identical, and "showing saved rows" and "offline" are
 * the same word.
 *
 * The states, in the order they are reached in a normal session:
 *
 * ```
 *  (start)      -> OFFLINE | SYNCING -> SYNCED -> SYNCING -> SYNCED …
 *                                 |
 *                                 +-> ERROR      (a request failed while a
 *                                                network was present)
 *  any          -> OFFLINE        (the network went away)
 * ```
 *
 * - [Offline] — no usable network. **Only** this state justifies the "saved
 *   data" framing, because the rows on screen genuinely came from the cache.
 * - [Syncing] — a safe read is in flight. Data on screen may be stale but is
 *   labelled as such; mutations stay disabled.
 * - [Synced] — the last read succeeded and the cache is current.
 * - [Error] — the last read **failed while a network was present**. This is the
 *   state the old boolean could not represent, and the one that made a backend
 *   failure read as "offline". The operator is told the store could not be
 *   reached and to retry, not to check their connection.
 * - [Online] — a network is present and no read has been attempted yet (the
 *   first moment after sign-in, before the first request completes).
 */
sealed interface ConnectionState {

    /** A network with internet capability is available. */
    data object Online : ConnectionState

    /** No usable network. Cached rows may be on screen. */
    data object Offline : ConnectionState

    /** A safe read is currently in flight. */
    data object Syncing : ConnectionState

    /** The last safe read succeeded; the cache is current. */
    data object Synced : ConnectionState

    /**
     * The last safe read failed **while the network was available**.
     *
     * [message] is operator-facing copy from [com.vedant.admin.core.error.ErrorCopy],
     * never a raw code.
     */
    data class Error(val message: String) : ConnectionState

    /** True only for [Offline] — the one state that permits "showing saved data". */
    val isOffline: Boolean get() = this is Offline

    /**
     * True when a mutation cannot reach the server. Both [Offline] and [Error]
     * qualify, which is why the UI must not decide this from [isOffline] alone.
     */
    val isBlocked: Boolean get() = this is Offline || this is Error
}
