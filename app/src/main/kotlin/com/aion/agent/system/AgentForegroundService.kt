package com.aion.agent.system

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aion.agent.MainActivity
import com.aion.agent.R
import com.aion.agent.llm.ModelManager
import com.aion.agent.util.AionLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Foreground service that keeps AION alive in the background.
 *
 * Responsibilities:
 *  - Persistent notification with model state indicator
 *  - Sleep controller integration (idle timeout → model unload)
 *  - Crash recovery via START_STICKY (restarted by system)
 *
 * Phase 2 adds:
 *  - Model loaded state in notification text
 *  - Sleep mode integration
 *
 * MCP server (Phase 5) will run inside this same service.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var logger: AionLogger
    @Inject lateinit var sleepController: SleepController
    @Inject lateinit var modelManager: ModelManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        logger.d(TAG) { "Service created" }
        // Start the sleep mode idle timer
        sleepController.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        logger.d(TAG) { "Service started, command: ${intent?.action}" }
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val modelIndicator = if (modelManager.isReady) {
            " · ${modelManager.loadedModelName?.substringBefore('.') ?: "Local"}"
        } else ""

        return NotificationCompat.Builder(this, NotificationChannels.AGENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AION is running$modelIndicator")
            .setContentText(getString(R.string.notification_agent_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Update the notification (e.g. after model state changes). */
    fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        sleepController.stop()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4242
        private const val TAG = "AgentFgs"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
