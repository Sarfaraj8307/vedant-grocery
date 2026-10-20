package com.vedant.admin

import android.app.Application
import com.vedant.admin.core.logging.AndroidLogSink
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.di.AppContainer

/**
 * Application entry point.
 *
 * Its only jobs are to install the logging sink and to build the single
 * [AppContainer]. `MainActivity` reads the container from here to construct its
 * `AppViewModelFactory`.
 *
 * Registered as `android:name=".VedantAdminApplication"` in the manifest — a
 * missing registration would silently break DI, so it is asserted by a test.
 */
class VedantAdminApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.install(AndroidLogSink())
        container = AppContainer(this)
    }
}
