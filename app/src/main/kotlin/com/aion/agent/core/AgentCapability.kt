package com.aion.agent.core

/**
 * AION's three capability tiers. Per AION_PLAN §4 this is a load-bearing decision:
 * the UI and skill registry degrade gracefully based on the current tier, never
 * relying on AccessibilityService or NotificationListenerService being present.
 *
 * Order: ascending privilege. Comparisons should use [ordinal] or [isAtLeast].
 */
enum class AgentCapability(val label: String, val description: String) {
    MINIMAL(
        "Chat Only",
        "No special permissions granted. Agent works via chat with cloud/local LLM. " +
            "No system access beyond what user grants.",
    ),
    PARTIAL(
        "Notification Access",
        "NotificationListenerService enabled — agent reads and manages notifications, " +
            "sends SMS, places calls. Cannot see screen.",
    ),
    FULL(
        "Full Access",
        "AccessibilityService enabled — agent sees screen, taps buttons, reads all apps, " +
            "observes system events. Maximum autonomy.",
    ),
    ;

    /**
     * Returns true when this tier has at least the privilege of [other].
     * Example: [FULL].isAtLeast([MINIMAL]) == true.
     */
    fun isAtLeast(other: AgentCapability): Boolean = ordinal >= other.ordinal
}
