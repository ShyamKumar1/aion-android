package com.aion.agent.core

import com.aion.agent.llm.CloudLlmEngine
import com.aion.agent.llm.LocalLlmEngine
import com.aion.agent.system.BatteryMonitor
import com.aion.agent.util.AionLogger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelRouterTest {

    private val localEngine = mockk<LocalLlmEngine>(relaxed = true)
    private val cloudEngine = mockk<CloudLlmEngine>(relaxed = true)
    private val batteryMonitor = mockk<BatteryMonitor>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `route when local ready returns local`() = runTest {
        coEvery { localEngine.isReady() } returns true
        every { localEngine.backendId } returns "local-llama"
        coEvery { cloudEngine.isReady() } returns false
        every { batteryMonitor.isCharging } returns false
        coEvery { batteryMonitor.batteryLevel } returns 80f

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(complexity = 0.3f)
        assert(selected.backendId == "local-llama")
    }

    @Test
    fun `route when local not ready falls to cloud`() = runTest {
        coEvery { localEngine.isReady() } returns false
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(complexity = 0.3f)
        assert(selected.backendId == "cloud")
    }

    @Test
    fun `route on low battery Auto mode prefers cloud`() = runTest {
        coEvery { localEngine.isReady() } returns true
        every { localEngine.backendId } returns "local-llama"
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"
        every { batteryMonitor.isCharging } returns false
        coEvery { batteryMonitor.batteryLevel } returns 15f

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(complexity = 0.3f)
        assert(selected.backendId == "cloud")
    }

    @Test
    fun `route with preference AlwaysLocal returns local`() = runTest {
        coEvery { localEngine.isReady() } returns true
        every { localEngine.backendId } returns "local-llama"
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(
            complexity = 0.3f,
            preference = ModelRouter.RoutePreference.AlwaysLocal,
        )
        assert(selected.backendId == "local-llama")
    }

    @Test
    fun `route with preference MaximumIntelligence returns cloud`() = runTest {
        coEvery { localEngine.isReady() } returns true
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(
            complexity = 0.3f,
            preference = ModelRouter.RoutePreference.MaximumIntelligence,
        )
        assert(selected.backendId == "cloud")
    }

    @Test(expected = AionException.InvalidConfigurationException::class)
    fun `route when nothing available throws`() = runTest {
        coEvery { localEngine.isReady() } returns false
        coEvery { cloudEngine.isReady() } returns false

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        router.selectEngine(complexity = 0.3f)
    }

    @Test
    fun `route BatterySaver on charger prefers local`() = runTest {
        coEvery { localEngine.isReady() } returns true
        every { localEngine.backendId } returns "local-llama"
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"
        every { batteryMonitor.isCharging } returns true
        coEvery { batteryMonitor.batteryLevel } returns 60f

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(
            complexity = 0.3f,
            preference = ModelRouter.RoutePreference.BatterySaver,
        )
        assert(selected.backendId == "local-llama")
    }

    @Test
    fun `route BatterySaver on battery prefers cloud`() = runTest {
        coEvery { localEngine.isReady() } returns true
        every { localEngine.backendId } returns "local-llama"
        coEvery { cloudEngine.isReady() } returns true
        every { cloudEngine.backendId } returns "cloud"
        every { batteryMonitor.isCharging } returns false
        coEvery { batteryMonitor.batteryLevel } returns 60f

        val router = ModelRouter(localEngine, cloudEngine, batteryMonitor, logger)
        val selected = router.selectEngine(
            complexity = 0.3f,
            preference = ModelRouter.RoutePreference.BatterySaver,
        )
        assert(selected.backendId == "cloud")
    }
}
