package com.aion.agent.core

import android.content.Context
import com.aion.agent.data.ConversationRepository
import com.aion.agent.llm.LlmEngine
import com.aion.agent.llm.LlmEvent
import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRequest
import com.aion.agent.llm.LlmRole
import com.aion.agent.llm.providers.LlmProviderRegistry
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillRegistry
import com.aion.agent.skills.SkillResult
import com.aion.agent.system.CapabilityManager
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent loop: Observe → Plan → Execute → Verify.
 *
 * Phase-1 implementation:
 *  - Observe: take the raw user text
 *  - Plan: [IntentClassifier] decides Chat vs ToolCall
 *  - Execute: route to either a skill or the LLM
 *  - Verify: surface the result to the caller (the ViewModel updates UI)
 *
 * Per AION_GUIDELINES N1, mutating skill actions return
 * [SkillResult.ConfirmationRequired] and the UI shows a confirmation card.
 * The loop does NOT execute them — the user must tap "Confirm."
 */
@Singleton
class AgentLoop @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LlmEngine,
    private val conversationRepository: ConversationRepository,
    private val intentClassifier: IntentClassifier,
    private val skillRegistry: SkillRegistry,
    private val capabilityManager: CapabilityManager,
    private val contextManager: ContextManager,
    private val logger: AionLogger,
) {

    /**
     * Process a user message. Returns a [Flow] of [AgentEvent]s that the
     * ChatViewModel consumes to update the UI.
     *
     * Flow of events:
     *  1. [AgentEvent.AssistantStarted] — we begin responding
     *  2. [AgentEvent.Token] x N — streaming text
     *  3. [AgentEvent.ToolCall] x N — any tool calls emitted by the LLM
     *  4. [AgentEvent.ConfirmationRequest] — if a mutating skill needs approval
     *  5. [AgentEvent.Done] — finished
     *
     * On error: [AgentEvent.Error] then [AgentEvent.Done].
     */
    fun processUserMessage(
        conversationId: String,
        userText: String,
    ): Flow<AgentEvent> = flow {
        emit(AgentEvent.AssistantStarted)

        // Save the user message immediately
        conversationRepository.appendMessage(
            conversationId = conversationId,
            role = "user",
            content = userText,
        )

        val intent = intentClassifier.classify(userText)
        when (intent) {
            is AgentIntent.Empty -> {
                emit(AgentEvent.Done(reason = DoneReason.EmptyInput))
                return@flow
            }
            is AgentIntent.Unknown -> {
                // Fall through to chat
                streamLlmReply(conversationId, userText).collect { emit(it) }
            }
            is AgentIntent.Chat -> {
                streamLlmReply(conversationId, userText).collect { emit(it) }
            }
            is AgentIntent.ToolCall -> {
                handleToolCall(conversationId, intent).collect { emit(it) }
            }
        }
        emit(AgentEvent.Done(reason = DoneReason.Normal))
    }
        .flowOn(Dispatchers.Default)
        .catch { t ->
            logger.e(TAG, t) { "Agent loop failed" }
            emit(AgentEvent.Error(t.message ?: t::class.java.simpleName))
            emit(AgentEvent.Done(reason = DoneReason.Error))
        }

    private suspend fun streamLlmReply(
        conversationId: String,
        userText: String,
    ): Flow<AgentEvent> = flow {
        val history = conversationRepository.getMessages(conversationId)
        val messages = contextManager.assemble(
            history = history,
            newUserText = userText,
        )

        val request = LlmRequest(
            systemPrompt = SYSTEM_PROMPT,
            messages = messages,
            tools = skillRegistry.definitions(),
            maxTokens = 1024,
            temperature = 0.4f,
        )

        val assistantMsg = conversationRepository.appendMessage(
            conversationId = conversationId,
            role = "assistant",
            content = "",
            status = "incomplete",
        )

        val buffer = StringBuilder()
        llmEngine.streamReply(request)
            .catch { t -> emit(LlmEvent.LlmError(t)) }
            .collect { event ->
                when (event) {
                    is LlmEvent.Token -> {
                        buffer.append(event.text)
                        emit(AgentEvent.Token(event.text))
                    }
                    is LlmEvent.ToolCall -> {
                        emit(AgentEvent.ToolCall(event.toolName, event.argumentsJson))
                    }
                    is LlmEvent.Done -> {
                        conversationRepository.updateMessageContent(
                            messageId = assistantMsg.id,
                            content = buffer.toString(),
                            status = "complete",
                        )
                        emit(AgentEvent.Finished)
                    }
                    is LlmEvent.LlmError -> {
                        conversationRepository.updateMessageContent(
                            messageId = assistantMsg.id,
                            content = buffer.toString(),
                            status = "failed",
                        )
                        emit(AgentEvent.Error(event.cause.message ?: "LLM error"))
                    }
                }
            }
    }.flowOn(Dispatchers.IO)

    private suspend fun handleToolCall(
        conversationId: String,
        intent: AgentIntent.ToolCall,
    ): Flow<AgentEvent> = flow {
        val skill = skillRegistry.byId(intent.skillId)
        if (skill == null) {
            emit(AgentEvent.Error("Skill not found: ${intent.skillId}"))
            return@flow
        }
        emit(AgentEvent.SkillInvoked(skill.definition.id, skill.definition.name))
        val result = skill.execute(intent.extractedParams)
        when (result) {
            is SkillResult.Success -> {
                emit(AgentEvent.SkillResult(result.summary))
                // Save a synthetic assistant message describing the action
                conversationRepository.appendMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = result.summary,
                )
            }
            is SkillResult.ConfirmationRequired -> {
                emit(AgentEvent.ConfirmationRequest(skill.definition.id, result.prompt))
            }
            is SkillResult.Failure -> {
                emit(AgentEvent.Error(result.reason))
            }
            is SkillResult.Timeout -> {
                emit(AgentEvent.Error("Skill timed out"))
            }
        }
    }

    private companion object {
        const val TAG = "AgentLoop"
        // Not a const because trimIndent() is a runtime function. The JVM
        // bytecode will still inline this as a private static field.
        val SYSTEM_PROMPT = """
            You are AION, a private on-device AI agent. Be concise, helpful, and
            never claim to perform actions you cannot actually perform. If asked
            to do something that requires a tool, use the tool. Otherwise, just
            respond.
        """.trimIndent()
    }
}

/**
 * Events emitted by the agent loop. The ViewModel maps these to UI state.
 */
sealed class AgentEvent {
    data object AssistantStarted : AgentEvent()
    data class Token(val text: String) : AgentEvent()
    data class ToolCall(val toolName: String, val argumentsJson: String) : AgentEvent()
    data class SkillInvoked(val skillId: String, val skillName: String) : AgentEvent()
    data class SkillResult(val summary: String) : AgentEvent()
    data class ConfirmationRequest(val skillId: String, val prompt: String) : AgentEvent()
    data object Finished : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class Done(val reason: DoneReason) : AgentEvent()
}

enum class DoneReason { Normal, EmptyInput, Cancelled, Error }
