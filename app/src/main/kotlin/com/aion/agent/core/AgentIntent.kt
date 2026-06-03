package com.aion.agent.core

/**
 * Result of classifying a user input. The classifier runs on a local 3B model
 * in Phase 2, or a simple heuristic in Phase 1. This is the input to the planner.
 */
sealed class AgentIntent {

    /** Free-form conversation. No tool calls. */
    data class Chat(val userText: String) : AgentIntent()

    /** User wants a specific tool/skill invoked. */
    data class ToolCall(
        val skillId: String,
        val confidence: Float,
        val extractedParams: Map<String, String> = emptyMap(),
    ) : AgentIntent()

    /** Cannot determine. Falls through to general LLM chat. */
    data class Unknown(val userText: String) : AgentIntent()

    /** Empty or whitespace-only input. */
    data object Empty : AgentIntent()
}

/**
 * A plan produced by the planning engine from an [AgentIntent]. A plan is a
 * sequence of [PlanStep]s the agent will execute in order.
 */
data class ExecutionPlan(
    val steps: List<PlanStep>,
) {
    val isEmpty: Boolean get() = steps.isEmpty()

    companion object {
        val Empty = ExecutionPlan(emptyList())
    }
}

/**
 * One step in a plan. Steps are executed in order. Failures abort the plan
 * unless the step declares [onError] = continue (not in Phase 1).
 */
sealed class PlanStep {
    abstract val description: String

    data class ToolInvocation(
        val skillId: String,
        val params: Map<String, String>,
        override val description: String,
    ) : PlanStep()

    data class LlmReply(
        val promptContext: String,
        override val description: String,
    ) : PlanStep()
}
