package com.aion.agent.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.aion.agent.BuildConfig
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
        // Cancel any pending restart alarm - we're alive
        cancelRestart(this)
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
        // Schedule a restart alarm in case the service is killed by the system
        scheduleRestart(this)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4242
        const val ACTION_RESTART = "${BuildConfig.APPLICATION_ID}.action.RESTART"
        private const val REQUEST_CODE_RESTART = 9001
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L
        private const val RESTART_DELAY_MS = 30_000L
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

        fun scheduleRestart(context: Context, delayMs: Long = RESTART_DELAY_MS) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_RESTART
            }
            val pending = PendingIntent.getService(
                context,
                REQUEST_CODE_RESTART,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs, pending)
        }

        fun cancelRestart(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_RESTART
            }
            val pending = PendingIntent.getService(
                context, REQUEST_CODE_RESTART, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
            )
            pending?.cancel()
        }
    }
}
