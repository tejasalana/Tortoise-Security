package com.skylinestudio.security.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.skylinestudio.security.service.TortoiseAccessibilityService

/**
 * Returns true if [serviceClass] is currently enabled in Accessibility Settings.
 *
 * How it works: Android stores enabled services as a colon-separated string of
 * "package/class" component names in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
 * We split that string and compare each entry against our service's ComponentName.
 *
 * Usage:
 *   if (!context.isAccessibilityServiceEnabled(TortoiseAccessibilityService::class.java)) {
 *       // launch Settings.ACTION_ACCESSIBILITY_SETTINGS to prompt the user
 *   }
 */
fun Context.isAccessibilityServiceEnabled(
    serviceClass: Class<out AccessibilityService> = TortoiseAccessibilityService::class.java,
): Boolean {
    val expected = ComponentName(this, serviceClass)

    val rawSetting = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(rawSetting)

    while (splitter.hasNext()) {
        ComponentName.unflattenFromString(splitter.next())?.let { cn ->
            if (cn == expected) return true
        }
    }
    return false
}

/**
 * Opens the system Accessibility Settings screen so the user can enable the service.
 * Call this when [isAccessibilityServiceEnabled] returns false.
 */
fun Context.openAccessibilitySettings() {
    startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
