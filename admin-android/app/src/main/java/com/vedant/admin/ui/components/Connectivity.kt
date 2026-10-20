package com.vedant.admin.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.vedant.admin.core.network.ConnectionMonitor

/**
 * Whether the device currently has a network, provided once at the top of the
 * app so every screen's [StateScaffold] shows the offline banner without each
 * screen having to thread the flag through.
 *
 * Defaults to `true`: a screen shown outside the provider (e.g. in a preview)
 * behaves as if online rather than flashing a spurious "offline" strip.
 */
val LocalOnlineStatus = compositionLocalOf { true }

/**
 * The [ConnectionMonitor] that owns the five explicit connection states
 * (§52). Provided once beside [LocalOnlineStatus].
 *
 * Screens should read this — not [LocalOnlineStatus] — when the wording shown to
 * the operator depends on *why* the app is blocked: [LocalOnlineStatus] can only
 * say "no network", which is wrong whenever the network is present and the
 * backend is what failed.
 *
 * Defaults to a monitor whose state is always
 * [com.vedant.admin.core.network.ConnectionState.Online], so previews and
 * isolated tests never render an offline banner.
 */
val LocalConnectionMonitor: androidx.compose.runtime.ProvidableCompositionLocal<ConnectionMonitor?> =
    compositionLocalOf { null }

/**
 * A short, non-blocking note explaining **why** a control is inert while the app
 * is offline (T24).
 *
 * A disabled control with no explanation reads as "broken" to an operator. This
 * sits next to the disabled affordance and says the change is simply not possible
 * right now — never that it is queued, because it is not: an unconfirmable
 * mutation is disabled, never deferred.
 */
@Composable
fun OfflineHint(
    modifier: Modifier = Modifier,
    text: String = "Unavailable offline — reconnect to make changes.",
    compact: Boolean = false,
) {
    Text(
        text = text,
        style = if (compact) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.labelMedium
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
