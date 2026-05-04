package com.skylinestudio.security.data

import android.os.Build

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "", // "PARENT" or "CHILD"
    val parentId: String? = null, // Only for children
    val deviceModel: String = Build.MODEL
)

data class Command(
    val action: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val params: Map<String, Any> = emptyMap()
)

data class AppUsageInfo(
    val packageName: String = "",
    val usageTimeMinutes: Long = 0
)

data class DeviceStatus(
    val currentApp: String = "",
    val batteryPercentage: Int = 0,
    val isScreenOn: Boolean = false,
    val topUsage: List<AppUsageInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)