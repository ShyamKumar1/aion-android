package com.aion.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aion.agent.system.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Root [Application] for AION.
 *
 * Responsibilities:
 *  - Hilt graph initialization
 *  - Notification channel registration
 *  - Custom [Configuration.Provider] for WorkManager so Hilt can inject @Worker classes
 */
@HiltAndroidApp
class AionApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.AGENT,
                getString(R.string.notification_channel_agent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_agent_desc)
                setShowBadge(false)
            },
        )
    }
}
