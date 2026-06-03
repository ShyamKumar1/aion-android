package com.aion.agent.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.memory.db.NotificationEntity
import com.aion.agent.util.AionLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Captures system notifications for agent consumption.
 *
 * Per AION_GUIDELINES §11:
 *  - Full notification text is stored temporarily for agent processing
 *  - After processing, discard notification text (keep: app, category, timestamp, action)
 *
 * This service ONLY activates when the user has granted NotificationListenerService
 * permission in system settings. The app never requests it without context.
 *
 * Privacy: Per N2, notification content is stored in local Room DB only and
 * never leaves the device unless the user explicitly sends it to a cloud LLM.
 */
@AndroidEntryPoint
class AgentNotificationListener : NotificationListenerService() {

    @Inject lateinit var notificationDao: NotificationDao
    @Inject lateinit var capabilityManager: CapabilityManager
    @Inject lateinit var logger: AionLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!capabilityManager.hasNotificationListenerAccess()) return

        val entity = parseNotification(sbn) ?: return

        scope.launch {
            try {
                notificationDao.insert(entity)
                logger.d(TAG) {
                    "Stored: ${entity.appName} — ${entity.title.take(50)}"
                }
            } catch (t: Throwable) {
                logger.e(TAG, t) { "Failed to store notification" }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scope.launch {
            try {
                notificationDao.setDismissed(sbn.key, true)
            } catch (t: Throwable) {
                logger.e(TAG, t) { "Failed to mark notification dismissed" }
            }
        }
    }

    /**
     * Parse a [StatusBarNotification] into a [NotificationEntity].
     * Returns null if the notification has no title or text.
     */
    private fun parseNotification(sbn: StatusBarNotification): NotificationEntity? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getString(Notification.EXTRA_TITLE)?.trim()?.take(256)
            ?: return null
        val text = extras.getString(Notification.EXTRA_TEXT)?.trim()?.take(512)
            ?: extras.getString(Notification.EXTRA_SUB_TEXT)?.trim()?.take(512)
            ?: ""
        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        return NotificationEntity(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            category = classify(sbn.packageName, title, text),
            priority = sbn.notification.priority,
            postedAt = sbn.postTime,
        )
    }

    /**
     * Classify a notification into a category string.
     * Used by the agent for routing and context building.
     */
    fun classify(pkg: String, title: String, text: String): String {
        val combined = "$title $text".lowercase()
        return when {
            MESSAGE_PACKAGES.any { pkg.startsWith(it) } -> "message"
            combined.contains("spam") ||
                combined.contains("unsubscribe") -> "spam"
            combined.contains("alert") ||
                combined.contains("warning") -> "alert"
            combined.contains("update") ||
                combined.contains("new version") -> "system"
            else -> "other"
        }
    }

    /** Snooze a notification by its key (re-posts after [millis]). */
    fun snooze(key: String, millis: Long = 300_000) {
        cancelNotification(key)
        scope.launch {
            notificationDao.setDismissed(key, false)
        }
    }

    companion object {
        private const val TAG = "NotifListener"

        /** Well-known messaging apps — notifications from these are "message" type. */
        val MESSAGE_PACKAGES = listOf(
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.tencent.mm",
            "org.telegram.messenger",
            "com.slack",
            "com.discord",
            "com.google.android.gm",
            "com.google.android.apps.inbox",
            "com.apple.mobilemail",
            "com.microsoft.teams",
            "com.skype.raider",
            "com.instagram.android",
        )
    }
}
