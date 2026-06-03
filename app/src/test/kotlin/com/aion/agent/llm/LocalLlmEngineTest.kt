package com.aion.agent.llm

import com.aion.agent.core.AionException
import com.aion.agent.util.AionLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalLlmEngineTest {

    private val bridge = mockk<LlamaBridge>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)
    private val engine = LocalLlmEngine(bridge, logger)

    @Test
    fun `backendId is local-llama`() {
        assert(engine.backendId == "local-llama")
    }

    @Test
    fun `isReady returns false when bridge not loaded`() = runTest {
        every { bridge.isLoaded } returns false
        val ready = engine.isReady()
        assert(!ready)
    }

    @Test
    fun `isReady returns true when bridge loaded`() = runTest {
        every { bridge.isLoaded } returns true
        val ready = engine.isReady()
        assert(ready)
    }

    @Test
    fun `currentModelName returns null when not loaded`() = runTest {
        every { bridge.loadedModelName } returns null
        val name = engine.currentModelName()
        assert(name == null)
    }

    @Test
    fun `currentModelName returns name when loaded`() = runTest {
        every { bridge.isLoaded } returns true
        every { bridge.loadedModelName } returns "qwen.gguf"
        val name = engine.currentModelName()
        assert(name == "qwen.gguf")
    }

    @Test
    fun `streamReply emits LlmError when bridge not loaded`() = runTest {
        every { bridge.isLoaded } returns false
        val events = engine.streamReply(
            LlmRequest(systemPrompt = "", messages = emptyList())
        ).toList()
        assert(events.any { it is LlmEvent.LlmError })
    }

    @Test
    fun `streamReply emits Done when bridge generates`() = runTest {
        every { bridge.isLoaded } returns true
        every { bridge.loadedModelName } returns "qwen.gguf"
        every { bridge.generate(any(), any(), any()) } returns
            kotlinx.coroutines.flow.flowOf("Hello", " world")

        val events = engine.streamReply(
            LlmRequest(systemPrompt = "Be helpful.", messages = emptyList())
        ).toList()
        assert(events.filterIsInstance<LlmEvent.Token>().any { it.text == "Hello" })
        assert(events.any { it is LlmEvent.Done })
    }

    @Test
    fun `loadModel delegates to bridge`() = runTest {
        every { bridge.loadModel(any(), any(), any()) } returns Result.success(Unit)
        val result = engine.loadModel("/path/to/model.gguf")
        assert(result.isSuccess)
        assert(engine.loadedModelPath == "/path/to/model.gguf")
    }

    @Test
    fun `loadModel returns failure on bridge error`() = runTest {
        every { bridge.loadModel(any(), any(), any()) } returns
            Result.failure(Exception("corrupt model"))
        val result = engine.loadModel("/path/to/bad.gguf")
        assert(result.isFailure)
    }

    @Test
    fun `unloadModel clears path and bridge`() = runTest {
        every { bridge.loadModel(any(), any(), any()) } returns Result.success(Unit)
        engine.loadModel("/path/to/model.gguf")
        assert(engine.loadedModelPath != null)

        every { bridge.unloadModel() } returns Unit
        engine.unloadModel()
        assert(engine.loadedModelPath == null)
    }
}
