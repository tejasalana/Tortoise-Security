package com.skylinestudio.security.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.skylinestudio.security.util.DriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

class TortoiseAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.tortoise.TRIGGER_SILENT_SCREENSHOT") {
                Log.d(TAG, "Silent screenshot triggered via broadcast")
                try {
                    takeSilentScreenshot()
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling screenshot broadcast", e)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        
        val filter = IntentFilter("com.tortoise.TRIGGER_SILENT_SCREENSHOT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Using RECEIVER_NOT_EXPORTED for internal app broadcasts
            registerReceiver(screenshotReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
        
        Log.d(TAG, "Accessibility service connected and receiver registered.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        currentAppPackage = packageName
    }

    internal fun takeSilentScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        
                        // We must wrap the buffer before closing it.
                        // However, we shouldn't close it until we are done with the bitmap
                        // if the bitmap is hardware-backed.
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)

                        if (bitmap != null) {
                            Log.d(TAG, "Screenshot captured successfully. Starting upload...")
                            serviceScope.launch {
                                try {
                                    val driveService = getDriveService()
                                    if (driveService != null) {
                                        val helper = DriveServiceHelper(driveService)
                                        val fileName = "Screenshot_${System.currentTimeMillis()}.jpg"
                                        val link = helper.uploadBitmap(bitmap, fileName)
                                        if (link != null) {
                                            updateFirestoreWithLink(link)
                                        }
                                    } else {
                                        saveBitmapToDownloads(bitmap)
                                        Log.w(TAG, "Skipping upload: Drive service is null (user might not be signed in to Google)")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to upload screenshot to Drive", e)
                                } finally {
                                    // Safely close the buffer after the bitmap has been processed (compressed)
                                    try {
                                        hardwareBuffer.close()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error closing hardware buffer", e)
                                    }
                                }
                            }
                        } else {
                            hardwareBuffer.close()
                            Log.e(TAG, "Failed to wrap hardware buffer into bitmap")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception during takeScreenshot call", e)
            }
        } else {
            Log.w(TAG, "Silent screenshot requires Android 11+")
        }
    }
    // TODO: Remove this after testing
    private fun saveBitmapLocally(bitmap: Bitmap) {
        Thread {
            try {
                val name = "Capture_${System.currentTimeMillis()}.jpg"
                val file = File(filesDir, name)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                Log.d("Tortoise", "SILENT SUCCESS: Saved to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("Tortoise", "Save failed", e)
            }
        }.start()
    }

    private fun saveBitmapToDownloads(bitmap: Bitmap) {
        Thread {
            try {
                // 1. Define the file path in the public Downloads folder
                val fileName = "Tortoise_Capture_${System.currentTimeMillis()}.jpg"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                // 2. Save the bitmap
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                Log.d("Tortoise", "SUCCESS: File is visible in Downloads: ${file.absolutePath}")

                // 3. IMPORTANT: Tell Android to "scan" the file so it shows up in the Gallery/Files app immediately
                MediaScannerConnection.scanFile(this, arrayOf(file.toString()), null) { path, uri ->
                    Log.d("Tortoise", "Scanned $path:")
                }

            } catch (e: Exception) {
                Log.e("Tortoise", "External save failed: ${e.message}")
            }
        }.start()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            unregisterReceiver(screenshotReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
    }

    private fun updateFirestoreWithLink(url: String) {
        val db = FirebaseFirestore.getInstance()
        val childId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val updates = mapOf(
            "lastScreenshotUrl" to url,
            "lastScreenshotTime" to Timestamp.now(),
            "status" to "Screenshot Updated"
        )

        db.collection("sync").document(childId)
            .collection("status").document("current")
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore updated with new screenshot link!")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update Firestore link", e)
            }
    }

    private fun getDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (googleAccount == null) {
            Log.e(TAG, "Google account not found. User not signed in to Google.")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = googleAccount.account

        val transport = NetHttpTransport()

        return Drive.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Tortoise Security")
            .build()
    }

    companion object {
        private const val TAG = "TortoiseA11y"
        var currentAppPackage: String = "Unknown"
            private set
        
        private var instance: TortoiseAccessibilityService? = null
        fun getInstance(): TortoiseAccessibilityService? = instance
    }
}
