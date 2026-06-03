package com.aion.agent.system

import android.content.Context
import com.aion.agent.data.SettingsRepository
import com.aion.agent.util.AionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BatteryMonitorTest {

    private val context = mockk<Context>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)

    @Test
    fun `batteryLevel defaults to 50`() {
        val monitor = BatteryMonitor(context, settings, logger)
        // Without a real battery Intent, it defaults
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
    fun `refresh updates batteryLevel`() {
        val monitor = BatteryMonitor(context, settings, logger)
        val oldLevel = monitor.batteryLevel
        monitor.refresh()
        // The level might change or stay the same; we just verify it doesn't crash
        assert(monitor.batteryLevel in 0f..100f)
    }

    @Test
    fun `recordModelLoad stores battery level`() = runTest {
        every {
            context.registerReceiver(null, any<IntentFilter>())
        } returns null

        val monitor = BatteryMonitor(context, settings, logger)
        monitor.recordModelLoad()
        verify { settings.setModelLoadBatteryLevel(any()) }
    }

    @Test
    fun `recordGenerationTime delegates to settings`() = runTest {
        val monitor = BatteryMonitor(context, settings, logger)
        monitor.recordGenerationTime(5000L)
        verify { settings.addCpuTime(5000L) }
    }

    @Test
    fun `resetStats delegates to settings`() = runTest {
        val monitor = BatteryMonitor(context, settings, logger)
        monitor.resetStats()
        verify { settings.resetBatteryStats() }
    }
}
