package com.aion.agent.llm

import android.content.Context
import com.aion.agent.core.AionException
import com.aion.agent.util.AionLogger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelManagerTest {

    private val engine = mockk<LocalLlmEngine>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `initial state is Idle`() {
        every { engine.isReady() } returns false
        val mgr = ModelManager(engine, context, logger)
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `loadClassifier updates state to Ready on success`() = runTest {
        every { engine.isReady() } returns false andThen true
        coEvery { engine.loadModel(any(), any(), any()) } returns Result.success(Unit)
        coEvery { engine.currentModelName() } returns "qwen.gguf"

        val mgr = ModelManager(engine, context, logger)
        val result = mgr.loadClassifier("/models/qwen.gguf")
        assert(result.isSuccess)
        assert(mgr.state.value is ModelState.Ready)
        val ready = mgr.state.value as ModelState.Ready
        assert(ready.modelName == "qwen.gguf")
    }

    @Test
    fun `loadClassifier same model already loaded returns success`() = runTest {
        every { engine.isReady() } returns true
        coEvery { engine.currentModelName() } returns "qwen.gguf"

        val mgr = ModelManager(engine, context, logger)
        val result = mgr.loadClassifier("/models/qwen.gguf")
        assert(result.isSuccess)
        // Should not call loadModel again
        verify(exactly = 0) { engine.loadModel(any(), any(), any()) }
    }

    @Test
    fun `unloadClassifier returns to Idle`() = runTest {
        every { engine.isReady() } returns true
        coEvery { engine.unloadModel() } returns Unit

        val mgr = ModelManager(engine, context, logger)
        mgr.markReady("qwen.gguf")
        mgr.unloadClassifier()
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `unloadClassifier safe when not loaded`() = runTest {
        every { engine.isReady() } returns false

        val mgr = ModelManager(engine, context, logger)
        mgr.unloadClassifier() // Should not throw
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `markReady sets state`() {
        every { engine.isReady() } returns false
        val mgr = ModelManager(engine, context, logger)
        mgr.markReady("qwen.gguf")
        assert(mgr.state.value is ModelState.Ready)
        assert((mgr.state.value as ModelState.Ready).modelName == "qwen.gguf")
    }

    @Test
    fun `isReady reflects state`() {
        every { engine.isReady() } returns false
        val mgr = ModelManager(engine, context, logger)
        assert(!mgr.isReady)
        mgr.markReady("qwen.gguf")
        assert(mgr.isReady)
    }

    @Test
    fun `loadedModelName returns null when idle`() {
        every { engine.isReady() } returns false
        val mgr = ModelManager(engine, context, logger)
        assert(mgr.loadedModelName == null)
    }
}
