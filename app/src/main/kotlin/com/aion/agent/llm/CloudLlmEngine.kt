package com.aion.agent.llm

import com.aion.agent.core.AionException
import com.aion.agent.data.ProviderRepository
import com.aion.agent.llm.providers.OpenAiChatRequest
import com.aion.agent.llm.providers.OpenAiFunction
import com.aion.agent.llm.providers.OpenAiMessage
import com.aion.agent.llm.providers.OpenAiStreamChunk
import com.aion.agent.llm.providers.OpenAiTool
import com.aion.agent.llm.providers.ProviderConfig
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.util.AionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI-compatible cloud LLM engine. Uses raw HTTP (not OkHttp SSE) for full
 * control over error handling and response body capture.
 *
 * Sends POST /v1/chat/completions with stream=true, reads the SSE body
 * line-by-line, and emits tokens as [LlmEvent.Token].
 *
 * On error the full response body is captured and included in the message.
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

    override fun streamReply(request: LlmRequest): Flow<LlmEvent> = flow {
        val provider = providerRepository.activeProviderOrThrow()
        val apiKey = providerRepository.activeApiKey()
            ?: throw AionException.ProviderAuthException(provider.displayName)
        val model = providerRepository.activeModelId()
            ?: throw AionException.InvalidConfigurationException("No model selected")

        android.util.Log.d("CloudLlmDebug", "URL=${provider.baseUrl.trimEnd('/') + "/chat/completions"}")
        android.util.Log.d("CloudLlmDebug", "Provider=${provider.id}, Key=${apiKey.take(8)}..., Model=$model")
        android.util.Log.d("CloudLlmDebug", "Headers: Auth=${provider.apiKeyHeader}, UA=${provider.defaultHeaders["User-Agent"]}")

        val requestBody = json.encodeToString(
            OpenAiChatRequest.serializer(),
            buildChatRequest(model, request, provider, request.tools),
        )
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header(provider.apiKeyHeader, provider.apiKeyPrefix + apiKey)
            .apply { for ((k, v) in provider.defaultHeaders) header(k, v) }
            .post(requestBody.toRequestBody(JSON_MEDIA))
            .build()

        logger.d(TAG) { "→ ${provider.displayName}/$model (${request.messages.size} msgs)" }

        // Execute the HTTP call (blocking, inside flowOn(IO))
        val response = httpClient.newCall(httpRequest).execute()

        // Handle error responses with full body capture
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "(empty)"
            val detail = try {
                val errObj = json.decodeFromString<JsonObject>(errorBody)
                val msg = errObj["error"]
                when (msg) {
                    is JsonObject -> msg["message"]?.let { it.toString().trim('"') } ?: msg.toString()
                    else -> msg?.toString() ?: errorBody.take(200)
                }
            } catch (_: Exception) {
                errorBody.take(200)
            }
            throw when (response.code) {
                401, 403 -> AionException.ProviderAuthException(provider.displayName)
                429 -> AionException.ProviderRateLimitException(provider.displayName)
                else -> AionException.ProviderHttpException(provider.displayName, response.code, detail)
            }
        }

        // Parse SSE stream
        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        val toolArgs = mutableMapOf<Int, StringBuilder>()
        val toolNames = mutableMapOf<Int, String>()
        val toolIds = mutableMapOf<Int, String>()
        var reachedEnd = false

        while (!reachedEnd) {
            val line = reader.readLine() ?: break
            if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                val chunk = try {
                    json.decodeFromString(OpenAiStreamChunk.serializer(), data)
                } catch (t: Throwable) {
                    logger.w(TAG) { "Bad SSE: ${data.take(100)}" }
                    continue
                }

                for (choice in chunk.choices) {
                    val content = choice.delta.content
                    if (!content.isNullOrEmpty()) {
                        emit(LlmEvent.Token(content))
                    }
                    val deltas = choice.delta.toolCalls
                    if (deltas != null) {
                        for (d in deltas) {
                            if (d.function?.name != null) toolNames[d.index] = d.function.name
                            if (d.id != null) toolIds[d.index] = d.id
                            if (!d.function?.arguments.isNullOrEmpty()) {
                                toolArgs.getOrPut(d.index) { StringBuilder() }.append(d.function!!.arguments)
                            }
                        }
                    }
                    if (choice.finishReason != null && choice.finishReason != "null") {
                        reachedEnd = true
                    }
                }
                if (chunk.usage != null) {
                    emit(LlmEvent.Done(mapUsage(chunk.usage)))
                }
            }
        }

        // Emit buffered tool calls
        for ((index, args) in toolArgs) {
            val name = toolNames[index] ?: continue
            emit(LlmEvent.ToolCall(toolName = name, argumentsJson = args.toString()))
        }

        // Ensure Done is always emitted
        emit(LlmEvent.Done(null))
    }.flowOn(Dispatchers.IO)

    private fun mapUsage(u: com.aion.agent.llm.providers.OpenAiUsage): LlmUsage =
        LlmUsage(
            promptTokens = u.promptTokens,
            completionTokens = u.completionTokens,
            totalTokens = u.totalTokens,
        )

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
        return OpenAiChatRequest(
            model = model,
            messages = messages,
            temperature = request.temperature,
            max_tokens = request.maxTokens,
            stream = request.stream,
            tools = if (skills.isNotEmpty() && provider.supportsToolCalling) {
                skills.map { it.toOpenAiTool() }
            } else null,
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
                    for (p in parameters.filter { it.required }) add(p.name)
                })
            },
        ),
    )

    private companion object {
        const val TAG = "CloudLlm"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
