package com.skylinestudio.security.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Only auto-start the service for a signed-in child.
        // If no user is cached, the service will stop itself anyway,
        // but we skip the intent entirely to avoid a pointless start.
        if (Firebase.auth.currentUser == null) {
            Log.d(TAG, "Boot received — no signed-in user, skipping service start.")
            return
        }

        Log.d(TAG, "Boot completed — starting MainMonitoringService.")

        val serviceIntent = Intent(context, MainMonitoringService::class.java)

        // startForegroundService required on API 26+ for services that call
        // startForeground(); plain startService() would crash on Oreo+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
