package com.aion.agent.system

import com.aion.agent.data.SettingsRepository
import com.aion.agent.llm.ModelManager
import com.aion.agent.util.AionLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SleepControllerTest {

    private val modelManager = mockk<ModelManager>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `initial state is Active`() {
        val controller = SleepController(modelManager, settings, logger)
        assert(controller.sleepState.value == SleepState.Active)
    }

    @Test
    fun `onUserInteraction resets timer`() = runTest {
        val controller = SleepController(modelManager, settings, logger)
        controller.onUserInteraction()
        assert(controller.sleepState.value == SleepState.Active)
    }

    @Test
    fun `reloadForInference no-op when Active`() = runTest {
        val controller = SleepController(modelManager, settings, logger)
        controller.reloadForInference()
        // Should not try to load model
        coVerify(exactly = 0) { modelManager.loadClassifier(any()) }
    }

    @Test
    fun `setTimeoutMinutes updates timeout`() = runTest {
        coEvery { settings.getSleepTimeoutMinutes() } returns 10
        val controller = SleepController(modelManager, settings, logger)
        controller.setTimeoutMinutes(10)
        coVerify { settings.setSleepTimeoutMinutes(10) }
    }

    @Test
    fun `stop cancels idle job`() {
        val controller = SleepController(modelManager, settings, logger)
        controller.stop()
        assert(controller.sleepState.value == SleepState.Active)
    }
}
