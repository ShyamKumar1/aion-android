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
import com.aion.agent.util.AionLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps AION alive in the background.
 *
 * Per AION_PLAN §11 (Phase 1 Week 2):
 *  - Persistent notification
 *  - Battery optimization exclusion prompt (handled at app start, not here)
 *  - Wake lock management (deferred to Phase 2 when inference is local)
 *  - Crash recovery via AlarmManager watchdog (Phase 2)
 *
 * In Phase 1 the service exists to (a) keep the process alive while the
 * chat screen is open and (b) provide the platform-level "agent running"
 * indicator. MCP server (Phase 5) will run inside this same service.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var logger: AionLogger

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        logger.d(TAG) { "Service created" }
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
        return NotificationCompat.Builder(this, NotificationChannels.AGENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_agent_title))
            .setContentText(getString(R.string.notification_agent_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
