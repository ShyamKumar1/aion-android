package com.aion.agent.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aion.agent.core.AgentEvent
import com.aion.agent.core.AgentLoop
import com.aion.agent.data.ConversationRepository
import com.aion.agent.data.ProviderRepository
import com.aion.agent.skills.SkillResult
import com.aion.agent.skills.builtin.SmsSkill
import com.aion.agent.system.AgentForegroundService
import com.aion.agent.util.AionLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [ChatScreen]. Translates user input into [AgentLoop] calls and
 * surfaces streamed events as UI state.
 *
 * Per AION_GUIDELINES §4:
 *  - Does not import anything from androidx.compose
 *  - Holds a single [StateFlow] for UI
 *  - Calls [AgentLoop] for the heavy lifting
 *  - Handles SMS confirmations by invoking [SmsSkill.sendNow]
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentLoop: AgentLoop,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val smsSkill: SmsSkill,
    private val logger: AionLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        // Start the foreground service so the chat is hosted by an
        // ongoing process the OS won't aggressively kill.
        AgentForegroundService.start(appContext)
        viewModelScope.launch {
            val ready = providerRepository.hasActiveProvider()
            _uiState.update { it.copy(isReady = ready) }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendClicked() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isResponding) return
        if (!state.isReady) {
            _uiState.update { it.copy(errorMessage = "Set a cloud provider in Settings first.") }
            return
        }
        send(text)
    }

    private fun send(text: String) {
        _uiState.update {
            it.copy(
                inputText = "",
                isResponding = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val conversationId = currentOrCreateConversation()
            conversationRepository.appendMessage(
                conversationId = conversationId,
                role = "user",
                content = text,
            )
            _uiState.update { st ->
                st.copy(
                    currentConversationId = conversationId,
                    messages = st.messages + ChatMessage(
                        id = "user-${System.nanoTime()}",
                        role = ChatMessage.Role.USER,
                        content = text,
                    ),
                )
            }
            startStream(conversationId, text)
        }
    }

    private suspend fun currentOrCreateConversation(): String {
        val existing = _uiState.value.currentConversationId
        if (existing != null) return existing
        val convo = conversationRepository.getOrCreateConversation()
        _uiState.update { it.copy(currentConversationId = convo.id) }
        return convo.id
    }

    private fun startStream(conversationId: String, userText: String) {
        val assistantId = "assistant-${System.nanoTime()}"
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ChatMessage(
                    id = assistantId,
                    role = ChatMessage.Role.ASSISTANT,
                    content = "",
                    isStreaming = true,
                ),
            )
        }

        streamingJob = viewModelScope.launch(Dispatchers.IO) {
            agentLoop.processUserMessage(conversationId, userText).collect { event ->
                handleAgentEvent(assistantId, event)
            }
            _uiState.update {
                it.copy(
                    isResponding = false,
                    messages = it.messages.map { m ->
                        if (m.id == assistantId) m.copy(isStreaming = false) else m
                    },
                )
            }
        }
    }

    private fun handleAgentEvent(assistantId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.Token -> {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { m ->
                            if (m.id == assistantId) {
                                m.copy(content = m.content + event.text)
                            } else m
                        },
                    )
                }
            }
            is AgentEvent.ToolCall -> {
                logger.d("ChatVM") { "Tool call: ${event.toolName}" }
            }
            is AgentEvent.SkillInvoked -> {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + ChatMessage(
                            id = "skill-${System.nanoTime()}",
                            role = ChatMessage.Role.TOOL,
                            content = "Invoked: ${event.skillName}",
                        ),
                    )
                }
            }
            is AgentEvent.SkillResult -> {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + ChatMessage(
                            id = "skill-result-${System.nanoTime()}",
                            role = ChatMessage.Role.SYSTEM,
                            content = event.summary,
                        ),
                    )
                }
            }
            is AgentEvent.ConfirmationRequest -> {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + ChatMessage(
                            id = "confirm-${System.nanoTime()}",
                            role = ChatMessage.Role.SYSTEM,
                            content = event.prompt,
                            pendingConfirmation = ConfirmationRequest(
                                skillId = event.skillId,
                                prompt = event.prompt,
                            ),
                        ),
                    )
                }
            }
            is AgentEvent.Error -> {
                _uiState.update { it.copy(errorMessage = event.message) }
            }
            is AgentEvent.AssistantStarted,
            is AgentEvent.Finished,
            is AgentEvent.Done -> {
                // No-op in UI
            }
        }
    }

    /**
     * Called by the UI when the user taps "Confirm" on a pending
     * confirmation. For Phase 1 the only confirmable skill is SMS.
     */
    fun onConfirm(confirm: ConfirmationRequest) {
        val conversationId = _uiState.value.currentConversationId ?: return
        if (confirm.skillId != "sms.send") {
            _uiState.update { it.copy(errorMessage = "This action cannot be confirmed yet.") }
            return
        }
        // Parse phone + body out of the confirmation prompt. The SmsSkill
        // emits the prompt as: Send SMS to <num>: "<body>"?
        val phoneMatch = Regex("""Send SMS to (\+?\d[\d\s-]+):""").find(confirm.prompt)
        val to = phoneMatch?.groupValues?.getOrNull(1)?.replace(Regex("""[\s-]"""), "") ?: run {
            _uiState.update { it.copy(errorMessage = "Could not parse phone number.") }
            return
        }
        val body = confirm.prompt.substringAfter("\"", "").substringBefore("\"", "")

        viewModelScope.launch(Dispatchers.IO) {
            val result = smsSkill.sendNow(to, body)
            val summary = when (result) {
                is SkillResult.Success -> result.summary
                is SkillResult.Failure -> "Send failed: ${result.reason}"
                is SkillResult.ConfirmationRequired -> "Send cancelled"
                is SkillResult.Timeout -> "Send timed out"
            }
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatMessage(
                        id = "sms-result-${System.nanoTime()}",
                        role = ChatMessage.Role.SYSTEM,
                        content = summary,
                    ),
                )
            }
            conversationRepository.appendMessage(
                conversationId = conversationId,
                role = "system",
                content = summary,
            )
        }
    }

    fun onCancel(@Suppress("UNUSED_PARAMETER") confirm: ConfirmationRequest) {
        // Phase 1: the confirmation card is dismissed by Compose local state.
        // A persistent "denied" trail is recorded by appending a system message
        // to the conversation history.
        val conversationId = _uiState.value.currentConversationId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepository.appendMessage(
                conversationId = conversationId,
                role = "system",
                content = "User cancelled the action.",
            )
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }
}
