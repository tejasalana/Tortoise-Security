package com.skylinestudio.security.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.skylinestudio.security.repo.FirebaseRepo
import com.skylinestudio.security.util.UsageTracker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.skylinestudio.security.R
import com.skylinestudio.security.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandJob: Job? = null
    private lateinit var usageTracker: UsageTracker

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        usageTracker = UsageTracker(this)
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        startAsForeground()

        val childId = Firebase.auth.currentUser?.uid
        if (childId == null) {
            Log.w(TAG, "No signed-in user — stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Ensure we only have one active collector even if onStartCommand is called multiple times
        commandJob?.cancel()
        commandJob = serviceScope.launch {
            Log.d(TAG, "Starting command listener for child: $childId")
            FirebaseRepo.commandsFlow(childId).collectLatest { command ->
                Log.d(TAG, "Command received → action='${command.action}'")
                when (command.action) {
                    "GET_STATUS" -> {
                        reportStatus(childId)
                        triggerSilentScreenshot()
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun triggerSilentScreenshot() {
        Log.d(TAG, "Triggering silent screenshot via internal broadcast...")
        try {
            val triggerIntent = Intent("com.tortoise.TRIGGER_SILENT_SCREENSHOT")
            triggerIntent.setPackage(packageName)
            sendBroadcast(triggerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send screenshot broadcast", e)
        }
    }

    private fun reportStatus(childId: String) {
        serviceScope.launch {
            try {
                val currentApp = TortoiseAccessibilityService.Companion.currentAppPackage
                val status = usageTracker.getDeviceStatus(currentApp)
                FirebaseRepo.uploadDeviceStatus(childId, status)
                Log.d(TAG, "Status reported successfully for $childId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report status", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tortoise Security Monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Running background safety monitoring"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tortoise Security Active")
            .setContentText("Monitoring system for safety")
//            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "MonitoringService"
        const val CHANNEL_ID = "tortoise_security_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
