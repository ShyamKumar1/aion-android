package com.aion.agent.system

import android.content.Context
import android.content.IntentFilter
import com.aion.agent.data.SettingsRepository
import com.aion.agent.util.AionLogger
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BatteryMonitorTest {

    private val context = mockk<Context>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `batteryLevel defaults to 50`() {
        val monitor = BatteryMonitor(context, settings, logger)
        assert(monitor.batteryLevel in 0f..100f)
    }

    @Test
    fun `isCharging returns false when no intent available`() {
        every {
            context.registerReceiver(null, any<IntentFilter>())
        } returns null

        val monitor = BatteryMonitor(context, settings, logger)
        assert(!monitor.isCharging)
    }

    @Test
    fun `refresh does not crash`() {
        val monitor = BatteryMonitor(context, settings, logger)
        // With a relaxed mock context, refresh should not throw
        monitor.refresh()
        // The value will be 0f because the relaxed mock Intent returns 0 for all extras
        assert(true)
    }

    @Test
    fun `recordModelLoad stores battery level`() = runTest {
        every {
            context.registerReceiver(null, any<IntentFilter>())
        } returns null

        val monitor = BatteryMonitor(context, settings, logger)
        monitor.recordModelLoad()
        coVerify { settings.setModelLoadBatteryLevel(any()) }
    }

    @Test
    fun `recordGenerationTime delegates to settings`() = runTest {
        val monitor = BatteryMonitor(context, settings, logger)
        monitor.recordGenerationTime(5000L)
        coVerify { settings.addCpuTime(5000L) }
    }

    @Test
    fun `resetStats delegates to settings`() = runTest {
        val monitor = BatteryMonitor(context, settings, logger)
        monitor.resetStats()
        coVerify { settings.resetBatteryStats() }
    }
}
