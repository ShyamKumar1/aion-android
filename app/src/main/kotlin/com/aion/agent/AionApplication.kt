package com.aion.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aion.agent.system.NotificationChannels
import com.aion.agent.util.LogRepository
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

    @Inject
    lateinit var logRepo: LogRepository

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannels()
        setupCrashHandler()
    }

    /**
     * Captures otherwise-uncaught exceptions so they appear in the Event Log
     * instead of being silently lost. The system will still crash afterwards.
     */
    private fun setupCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logRepo.fatal(
                tag = "AION",
                t = throwable,
                message = "Uncaught exception on thread ${thread.name}",
                category = com.aion.agent.util.LogCategory.SYSTEM,
            )
            // Chain to the previous handler so Android still shows the crash dialog
            previous?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // Agent foreground service channel
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

        // Model download progress channel
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.DOWNLOADS,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "GGUF model download progress"
                setShowBadge(false)
            },
        )
    }
}
