package com.aion.agent.llm

import com.aion.agent.util.AionLogger
import io.mockk.mockk
import org.junit.Test

/**
 * NOTE: These tests verify the Kotlin wrapper contract, not the native code.
 * LlamaBridge delegates to JNI; in unit tests we verify the wrapper behavior
 * (error handling, null checks, etc.).
 *
 * The native library is arm64-v8a only; unit tests run on the host JVM (either
 * x86 via Robolectric or the dev machine) so [LlamaBridge.isAvailable] returns
 * false. All JNI methods are stubbed out at the Kotlin level.
 *
 * Integration tests require a physical device or arm64 emulator.
 */
class LlamaBridgeTest {

    private val logger = mockk<AionLogger>(relaxed = true)
    private val bridge = LlamaBridge(logger)

    @Test
    fun `isAvailable returns false when native library not loaded`() {
        // On x86 emulator / host JVM, System.loadLibrary("llamabridge") fails
        assert(!bridge.isAvailable)
    }

    @Test
    fun `loadModel when native not available returns failure`() {
        val result = bridge.loadModel("/path/to/model.gguf")
        assert(result.isFailure)
    }

    @Test
    fun `loadModel empty path returns failure`() {
        val result = bridge.loadModel("")
        assert(result.isFailure)
    }

    @Test
    fun `unloadModel safe when not loaded`() {
        // Should not throw when no model is loaded
        bridge.unloadModel()
    }

    @Test
    fun `isLoaded starts false`() {
        assert(!bridge.isLoaded)
    }

    @Test
    fun `loadedModelName starts null`() {
        assert(bridge.loadedModelName == null)
    }

    @Test
    fun `tokenCount returns 0 when no model loaded`() {
        assert(bridge.tokenCount("hello world") == 0)
    }

    @Test(expected = LlamaException::class)
    fun `generate throws when no model loaded`() {
        kotlinx.coroutines.test.runTest {
            bridge.generate("hello").collect { /* should not reach */ }
        }
    }
}
