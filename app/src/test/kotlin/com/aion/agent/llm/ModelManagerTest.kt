package com.aion.agent.llm

import android.content.Context
import com.aion.agent.core.AionException
import com.aion.agent.util.AionLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelManagerTest {

    private val engine = mockk<LocalLlmEngine>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `initial state is Idle`() = runTest {
        val mgr = ModelManager(engine, context, logger)
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `loadClassifier updates state to Ready on success`() = runTest {
        coEvery { engine.isReady() } returns true
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
        coEvery { engine.isReady() } returns true
        coEvery { engine.currentModelName() } returns "qwen.gguf"

        val mgr = ModelManager(engine, context, logger)
        val result = mgr.loadClassifier("/models/qwen.gguf")
        assert(result.isSuccess)
        coVerify(exactly = 0) { engine.loadModel(any(), any(), any()) }
    }

    @Test
    fun `unloadClassifier returns to Idle`() = runTest {
        coEvery { engine.isReady() } returns true

        val mgr = ModelManager(engine, context, logger)
        mgr.markReady("qwen.gguf")
        mgr.unloadClassifier()
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `unloadClassifier safe when not loaded`() = runTest {
        coEvery { engine.isReady() } returns false

        val mgr = ModelManager(engine, context, logger)
        mgr.unloadClassifier()
        assert(mgr.state.value is ModelState.Idle)
    }

    @Test
    fun `markReady sets state`() = runTest {
        val mgr = ModelManager(engine, context, logger)
        mgr.markReady("qwen.gguf")
        assert(mgr.state.value is ModelState.Ready)
        assert((mgr.state.value as ModelState.Ready).modelName == "qwen.gguf")
    }

    @Test
    fun `isReady reflects state`() = runTest {
        val mgr = ModelManager(engine, context, logger)
        assert(!mgr.isReady)
        mgr.markReady("qwen.gguf")
        assert(mgr.isReady)
    }

    @Test
    fun `loadedModelName returns null when idle`() = runTest {
        val mgr = ModelManager(engine, context, logger)
        assert(mgr.loadedModelName == null)
    }
}
