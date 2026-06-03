package com.aion.agent.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aion.agent.data.SettingsRepository
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks battery statistics for AION's battery dashboard.
 *
 * Per AION_GUIDELINES §6 (Battery Budget):
 *  - Tracks: % battery used by AION, CPU time, model loaded time
 *  - Reads: BatteryManager for level/charging status
 *
 * Phase 2 implementation tracks battery level and charging status.
 * Full per-process power tracking requires Android Vitals or PowerProfile,
 * which varies by OEM — deferred to Phase 3.
 */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val logger: AionLogger,
) {
    /** Last known battery level (0.0–100.0). Updated by [refresh]. */
    var batteryLevel: Float = 50f
        private set

    /** Whether the device is currently plugged in and charging. */
    val isCharging: Boolean
        get() {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

    /** Refresh battery level from the system. Safe to call frequently. */
    fun refresh() {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        batteryLevel = intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            level.toFloat() / scale.toFloat() * 100f
        } ?: 50f
    }

    /** Record a battery snapshot at model load time. */
    suspend fun recordModelLoad() {
        refresh()
        settings.setModelLoadBatteryLevel(batteryLevel.toInt())
        logger.d(TAG) { "Model loaded at ${batteryLevel}%" }
    }

    /** Record CPU time used by a generation cycle (milliseconds). */
    suspend fun recordGenerationTime(millis: Long) {
        settings.addCpuTime(millis)
    }

    /** Reset all battery stats (e.g. at user request). */
    suspend fun resetStats() {
        settings.resetBatteryStats()
    }

    companion object {
        private const val TAG = "BatteryMonitor"
    }
}
