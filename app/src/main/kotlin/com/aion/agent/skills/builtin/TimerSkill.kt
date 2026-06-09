package com.aion.agent.skills.builtin

import android.util.Log
import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in timer/alarm skill. Schedules a coroutine-based timer.
 * Phase 1 uses a simple coroutine delay; Phase 2 will use
 * WorkManager/AlarmManager for persistent scheduling.
 */
@Singleton
class TimerSkill @Inject constructor() : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "timer.set",
        name = "Set Timer",
        description = "Sets a timer for N minutes. Use when the user asks to set, start, or schedule a timer.",
        keywords = listOf("timer", "alarm", "remind", "minutes", "seconds", "set a timer"),
        parameters = listOf(
            com.aion.agent.skills.SkillParameter(
                name = "duration_minutes",
                description = "How many minutes until the timer fires",
                jsonType = "number",
                required = true,
            ),
            com.aion.agent.skills.SkillParameter(
                name = "label",
                description = "Optional label for the timer",
                jsonType = "string",
                required = false,
            ),
        ),
        requiredCapability = AgentCapability.MINIMAL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        return if ("timer" in lower || "remind me in" in lower) 0.7f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val minutes = params["duration_minutes"]?.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            return SkillResult.Failure(
                reason = "Invalid duration",
                summary = "Couldn't set timer — invalid duration.",
            )
        }

        // Schedule a coroutine delay-based timer
        // In a real implementation, this would use AlarmManager/WorkManager
        // For now, use a fire-and-forget coroutine to simulate the reminder
        GlobalScope.launch {
            delay(minutes * 60_000L)
            Log.i("TimerSkill", "Timer expired: $minutes minute timer finished")
        }

        return SkillResult.Success(
            output = "Timer set for $minutes minutes",
            summary = "I'll remind you in $minutes minutes.",
        )
    }
}
