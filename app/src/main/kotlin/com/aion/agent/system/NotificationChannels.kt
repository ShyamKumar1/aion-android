package com.aion.agent.system

/**
 * Notification channel IDs. Centralized so we can reference them in
 * [com.aion.agent.AionApplication] and the foreground service without stringly-typed drift.
 */
object NotificationChannels {
    const val AGENT = "aion_agent_channel"
    const val DOWNLOADS = "aion_downloads"
}
