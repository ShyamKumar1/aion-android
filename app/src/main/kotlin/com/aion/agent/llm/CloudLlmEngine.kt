package com.aion.agent.llm

import com.aion.agent.core.AionException
import com.aion.agent.data.ProviderRepository
import com.aion.agent.llm.providers.LlmProviderRegistry
import com.aion.agent.llm.providers.OpenAiChatRequest
import com.aion.agent.llm.providers.OpenAiFunction
import com.aion.agent.llm.providers.OpenAiMessage
import com.aion.agent.llm.providers.OpenAiStreamChunk
import com.aion.agent.llm.providers.OpenAiTool
import com.aion.agent.llm.providers.ProviderConfig
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI-compatible cloud LLM engine. Used by all three Phase-1 providers
 * (OpenRouter, Opencode Go, NVIDIA NIM). They all accept the same wire format
 * and emit Server-Sent Events the same way.
 *
 * Streaming: each SSE chunk is parsed as a delta. Token text is emitted as
 * [LlmEvent.Token] events. Final chunk includes a [LlmEvent.Done] with usage.
 *
 * This class is the only place that talks to the network for LLM calls.
 * Per AION_GUIDELINES §7, API keys are read from [ProviderRepository] at call
 * time — never passed via constructor, never logged.
 */
@Singleton
class CloudLlmEngine @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val providerRepository: ProviderRepository,
    private val logger: AionLogger,
) : LlmEngine {

    override val backendId: String = "cloud"

    override suspend fun isReady(): Boolean = providerRepository.hasActiveProvider()

    override suspend fun currentModelName(): String? = providerRepository.activeModelId()

    override fun streamReply(request: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val provider = providerRepository.activeProviderOrThrow()
        val apiKey = providerRepository.activeApiKey()
            ?: run {
                trySend(LlmEvent.LlmError(AionException.ProviderAuthException(provider.displayName)))
                close()
                return@callbackFlow
            }
        val model = providerRepository.activeModelId()
            ?: run {
                trySend(LlmEvent.LlmError(AionException.InvalidConfigurationException("No model selected")))
                close()
                return@callbackFlow
            }

        val body = json.encodeToString(
            OpenAiChatRequest.serializer(),
            buildChatRequest(model, request, provider, request.tools),
        )
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header(provider.apiKeyHeader, provider.apiKeyPrefix + apiKey)
        for ((k, v) in provider.defaultHeaders) {
            reqBuilder.header(k, v)
        }
        val httpRequest = reqBuilder.post(body.toRequestBody(JSON_MEDIA)).build()

        logger.d(TAG) {
            "→ ${provider.displayName}/$model (${request.messages.size} msgs, ${request.maxTokens} max)"
        }

        val factory = EventSources.createFactory(httpClient)
        // Buffers for tool call deltas, keyed by tool call index.
        val toolArgs = mutableMapOf<Int, StringBuilder>()
        val toolNames = mutableMapOf<Int, String>()
        val toolIds = mutableMapOf<Int, String>()

        val listener = object : EventSourceListener() {

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data.isBlank() || data == "[DONE]") return
                val chunk: OpenAiStreamChunk = try {
                    json.decodeFromString(OpenAiStreamChunk.serializer(), data)
                } catch (t: Throwable) {
                    logger.w(TAG) { "Skipped malformed SSE chunk: ${data.take(200)}" }
                    return
                }
                for (choice in chunk.choices) {
                    val content = choice.delta.content
                    if (!content.isNullOrEmpty()) {
                        trySend(LlmEvent.Token(content))
                    }
                    val deltas = choice.delta.toolCalls
                    if (deltas != null) {
                        for (d in deltas) {
                            if (d.function?.name != null) toolNames[d.index] = d.function.name
                            if (d.id != null) toolIds[d.index] = d.id
                            val args = d.function?.arguments
                            if (!args.isNullOrEmpty()) {
                                toolArgs.getOrPut(d.index) { StringBuilder() }.append(args)
                            }
                        }
                    }
                }
                if (chunk.usage != null) {
                    trySend(
                        LlmEvent.Done(
                            LlmUsage(
                                promptTokens = chunk.usage.promptTokens,
                                completionTokens = chunk.usage.completionTokens,
                                totalTokens = chunk.usage.totalTokens,
                            ),
                        ),
                    )
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val cause = mapHttpError(provider, response, t)
                trySend(LlmEvent.LlmError(cause))
                close(cause)
            }

            override fun onClosed(eventSource: EventSource) {
                // Emit any buffered tool calls as a single ToolCall event
                for ((index, args) in toolArgs) {
                    val name = toolNames[index] ?: continue
                    trySend(LlmEvent.ToolCall(toolName = name, argumentsJson = args.toString()))
                }
                close()
            }
        }

        val source = factory.newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun mapHttpError(
        provider: ProviderConfig,
        response: Response?,
        cause: Throwable?,
    ): AionException {
        if (response == null) {
            return AionException.NetworkUnavailableException().also {
                cause?.let { c -> it.initCause(c) }
            }
        }
        return when (response.code) {
            401, 403 -> AionException.ProviderAuthException(provider.displayName)
            429 -> AionException.ProviderRateLimitException(provider.displayName)
            in 500..599 -> AionException.ProviderHttpException(
                provider.displayName,
                response.code,
                response.message,
            )
            else -> AionException.ProviderHttpException(
                provider.displayName,
                response.code,
                response.message,
            )
        }
    }

    private fun buildChatRequest(
        model: String,
        request: LlmRequest,
        provider: ProviderConfig,
        skills: List<SkillDefinition>,
    ): OpenAiChatRequest {
        val messages = mutableListOf<OpenAiMessage>()
        if (request.systemPrompt.isNotBlank()) {
            messages += OpenAiMessage(role = "system", content = request.systemPrompt)
        }
        for (m in request.messages) {
            messages += OpenAiMessage(
                role = when (m.role) {
                    LlmRole.SYSTEM -> "system"
                    LlmRole.USER -> "user"
                    LlmRole.ASSISTANT -> "assistant"
                    LlmRole.TOOL -> "tool"
                },
                content = m.content.takeIf { it.isNotEmpty() },
                toolCallId = m.toolCallId,
                name = m.toolName,
            )
        }
        val tools = if (skills.isNotEmpty() && provider.supportsToolCalling) {
            skills.map { it.toOpenAiTool() }
        } else {
            null
        }
        return OpenAiChatRequest(
            model = model,
            messages = messages,
            temperature = request.temperature,
            max_tokens = request.maxTokens,
            stream = request.stream,
            tools = tools,
        )
    }

    private fun SkillDefinition.toOpenAiTool(): OpenAiTool = OpenAiTool(
        function = OpenAiFunction(
            name = id,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    for (p in parameters) {
                        put(p.name, buildJsonObject {
                            put("type", p.jsonType)
                            put("description", p.description)
                        })
                    }
                })
                put("required", buildJsonArray {
                    for (p in parameters.filter { it.required }) {
                        add(p.name)
                    }
                })
            },
        ),
    )

    private companion object {
        const val TAG = "CloudLlm"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
