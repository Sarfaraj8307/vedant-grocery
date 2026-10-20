package com.vedant.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as VedantAdminApplication).container

        setContent {
            val isOnline by container.connectivity.isOnline.collectAsStateWithLifecycle()
            val reduceMotion = rememberReduceMotion()

            VedantAdminTheme {
                CompositionLocalProvider(
                    LocalOnlineStatus provides isOnline,
                    LocalConnectionMonitor provides container.connectionMonitor,
                    LocalReduceMotion provides reduceMotion,
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
