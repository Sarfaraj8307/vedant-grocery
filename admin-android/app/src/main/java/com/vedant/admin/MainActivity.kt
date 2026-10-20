package com.vedant.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vedant.admin.ui.components.LocalConnectionMonitor
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.navigation.VedantNavHost
import com.vedant.admin.ui.theme.LocalAppearanceMode
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantAdminTheme
import com.vedant.admin.ui.theme.rememberReduceMotion

/**
 * Single-activity host.
 *
 * It reads the [AppContainer] from the `Application`, applies the theme, and
 * hands the whole screen to [VedantNavHost], which owns every destination and the
 * auth/admin gate. No business logic lives here.
 *
 * It also provides [LocalOnlineStatus] once, from the container's connectivity
 * observer, so every screen's `StateScaffold` shows the offline banner and
 * mutation controls can disable themselves without threading the flag through.
 *
 * The appearance choice (§8) is resolved here, at the one boundary where the
 * theme is chosen: the persisted mode from the container is mapped to the
 * `darkTheme` flag (deferring to the system for `System default`), and the
 * choice itself is provided through [LocalAppearanceMode] for any screen that
 * needs to show which option is active.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as VedantAdminApplication).container

        setContent {
            val isOnline by container.connectivity.isOnline.collectAsStateWithLifecycle()
            val reduceMotion = rememberReduceMotion()

            // System dark mode, read once; the persisted choice decides whether
            // it matters. The flow is seeded synchronously, so the first frame
            // of a cold start already uses the saved theme.
            val systemInDark = isSystemInDarkTheme()
            val appearanceMode by container.appearanceRepository.mode
                .collectAsStateWithLifecycle()
            val darkTheme = appearanceMode.prefersDark(systemInDark)

            VedantAdminTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalOnlineStatus provides isOnline,
                    LocalConnectionMonitor provides container.connectionMonitor,
                    LocalReduceMotion provides reduceMotion,
                    LocalAppearanceMode provides appearanceMode,
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                        VedantNavHost(
                            container = container,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(insets),
                        )
                    }
                }
            }
        }
    }
}
