package com.dealmate.app

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.notifications.AlertWorkScheduler
import com.dealmate.app.notifications.PriceAlertNotifier
import com.dealmate.app.notifications.PushManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class DealMateApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var alertScheduler: AlertWorkScheduler
    @Inject lateinit var notifier: PriceAlertNotifier
    @Inject lateinit var pushManager: PushManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * Vietnamese is the default language whatever the device language is. On Android 13+ a language
     * picked in the system's per-app language settings wins; otherwise the in-app setting is applied.
     */
    private fun applyLanguage() {
        val systemChoice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppCompatDelegate.getApplicationLocales().isEmpty
        if (systemChoice) return
        val language = runBlocking { settings.current().language }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    override fun onCreate() {
        super.onCreate()
        applyLanguage()
        notifier.ensureChannel()
        pushManager.initIfConfigured()

        // Background alert delivery follows the settings: polling worker always, FCM when configured.
        appScope.launch {
            settings.settings
                .map { it.notificationsEnabled && it.isConfigured }
                .distinctUntilChanged()
                .collect { active ->
                    alertScheduler.sync(active)
                    if (active) pushManager.registerDevice()
                }
        }
    }
}
