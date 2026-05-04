package com.skylinestudio.security.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.skylinestudio.security.data.AppUsageInfo
import com.skylinestudio.security.data.DeviceStatus
import java.util.Calendar

class UsageTracker(private val context: Context) {

    fun getDeviceStatus(currentApp: String): DeviceStatus {
        return DeviceStatus(
            currentApp = currentApp,
            batteryPercentage = getBatteryPercentage(),
            isScreenOn = isScreenOn(),
            topUsage = getTopUsageStats(5),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isScreenOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun getTopUsageStats(limit: Int): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map {
                AppUsageInfo(
                    packageName = it.packageName,
                    usageTimeMinutes = it.totalTimeInForeground / 60000
                )
            }
    }
}