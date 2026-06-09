package com.aion.agent.skills.builtin

import com.aion.agent.core.AgentCapability
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.memory.db.NotificationEntity
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for reading recent device notifications.
 *
 * Requires [AgentCapability.PARTIAL] (NotificationListenerService enabled).
 * Queries the local [NotificationDao] for the most recent N notifications
 * and returns a human-readable summary.
 */
@Singleton
class NotificationSkill @Inject constructor(
    private val notificationDao: NotificationDao,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "notification.read",
        name = "Read Notifications",
        description = "Reads recent notifications from the device. " +
            "Use when the user asks about new notifications, alerts, " +
            "or what's been happening on their phone.",
        keywords = listOf(
            "notification", "notify", "alert", "notification history",
            "recent notifications", "new notifications", "what's new",
            "alerts", "notifications",
        ),
        parameters = listOf(
            SkillParameter(
                name = "limit",
                description = "Number of recent notifications to return (default 5)",
                jsonType = "integer",
                required = false,
            ),
        ),
        requiredCapability = AgentCapability.PARTIAL,
    )

    override fun canHandle(input: String): Float {
        return if (input.lowercase().contains("notification")) 0.5f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val limit = (params["limit"]?.toIntOrNull() ?: 5).coerceIn(1, 50)

        val notifications: List<NotificationEntity> = try {
            notificationDao.getRecent(limit)
        } catch (t: Throwable) {
            logger.e("NotificationSkill", t) { "Failed to query notifications" }
            return SkillResult.Failure(
                reason = t.message ?: "Database query failed",
                summary = "Couldn't read notifications from the database.",
            )
        }

        if (notifications.isEmpty()) {
            return SkillResult.Success(
                output = "No notifications found.",
                summary = "No recent notifications.",
            )
        }

        val sb = StringBuilder()
        sb.appendLine("Found ${notifications.size} recent notification(s):")
        sb.appendLine()
        notifications.forEachIndexed { i, n ->
            sb.appendLine("${i + 1}. [${n.appName}] ${n.title}")
            if (n.text.isNotBlank()) sb.appendLine("   ${n.text}")
            sb.appendLine("   Posted: ${formatTimestamp(n.postedAt)}")
            if (n.dismissed) sb.appendLine("   (dismissed)")
            sb.appendLine()
        }

        val output = sb.toString().trimEnd()

        return SkillResult.Success(
            output = output,
            summary = "Read ${notifications.size} recent notification(s).",
        )
    }

    /** Simple relative-time formatting for display. */
    private fun formatTimestamp(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            else -> "${diff / 86_400_000L}d ago"
        }
    }
}
