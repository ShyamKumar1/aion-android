package com.aion.agent.skills.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for opening a web search in the device browser. Per
 * AION_GUIDELINES §13 and N1, mutating actions that involve user context
 * (like launching the browser) return [SkillResult.ConfirmationRequired]
 * so the user can confirm before the browser opens.
 */
@Singleton
class WebSearchSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "web.search",
        name = "Web Search",
        description = "Opens a web search in the device browser. " +
            "Use when the user asks to search, look up, or find something online.",
        keywords = listOf(
            "search", "google", "browse", "internet", "web",
            "look up", "find online", "search for", "search the web",
            "lookup",
        ),
        parameters = listOf(
            SkillParameter(
                name = "query",
                description = "The search text to look up online",
                jsonType = "string",
                required = true,
            ),
        ),
        requiredCapability = AgentCapability.MINIMAL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        return if ("search" in lower || "look up" in lower) 0.4f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val query = params["query"]?.trim().orEmpty()
        if (query.isBlank()) {
            return SkillResult.Failure(
                reason = "Missing 'query' parameter",
                summary = "Couldn't search — need something to search for.",
            )
        }

        // Per AION_GUIDELINES N1, mutating actions require user confirmation.
        // Return a ConfirmationRequired result; the UI shows a confirm card.
        return SkillResult.ConfirmationRequired(
            prompt = "Search the web for \"${query.take(80)}${if (query.length > 80) "…" else ""}\"?",
            summary = "Awaiting confirmation",
        )
    }

    /**
     * Actually perform the web search. Called by ChatViewModel after the user
     * confirms. Launches the device browser with a Google search URL.
     */
    suspend fun searchNow(query: String): SkillResult {
        try {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=$encodedQuery")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            logger.i("WebSearchSkill") { "Browser opened for query: ${query.take(40)}" }
            return SkillResult.Success(
                output = "Searching for \"$query\"",
                summary = "Opened browser to search for \"$query\"",
            )
        } catch (t: Throwable) {
            logger.e("WebSearchSkill", t) { "Failed to open browser" }
            return SkillResult.Failure(
                reason = t.message ?: "Unknown error",
                summary = "Couldn't open browser: ${t.message ?: "unknown error"}",
            )
        }
    }
}
