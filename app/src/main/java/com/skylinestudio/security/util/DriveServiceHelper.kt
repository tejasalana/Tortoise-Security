package com.skylinestudio.security.util

import android.graphics.Bitmap
import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import java.io.ByteArrayOutputStream

/**
 * Helper class for Google Drive operations.
 * Note: This implementation is a placeholder for actual Google Drive API integration.
 * In a real-world scenario, you would use the Google Drive REST API or Play Services.
 */
//class DriveServiceHelper2 private constructor(private val context: Context) {

    /**
     * Uploads a bitmap to Google Drive.
     * Currently saves to a local file as a placeholder for the Drive upload logic.
     */
//    fun uploadBitmap(bitmap: Bitmap, fileName: String) {
//        try {
//            // Placeholder: In a real app, you'd use the Drive API to upload the bytes.
//            // For now, we'll log the "upload" and save locally to simulate work.
//            val outputStream = ByteArrayOutputStream()
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
//            val byteArray = outputStream.toByteArray()
//
//            Log.d("DriveServiceHelper", "Simulating upload of $fileName (${byteArray.size} bytes) to Google Drive...")
//
//            // Local save as proof of work
//            val file = File(context.cacheDir, fileName)
//            FileOutputStream(file).use { it.write(byteArray) }
//
//            // Real implementation would involve:
//            // val driveFile = com.google.api.services.drive.model.File()
//            // driveFile.name = fileName
//            // driveService.files().create(driveFile, ByteArrayContent("image/jpeg", byteArray)).execute()
//
//        } catch (e: Exception) {
//            Log.e("DriveServiceHelper", "Error during Drive upload simulation", e)
//            throw e
//        }
//    }

//    companion object {
//        @Volatile
//        private var INSTANCE: DriveServiceHelper? = null
//
//        fun getInstance(context: Context): DriveServiceHelper {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: DriveServiceHelper(context.applicationContext).also { INSTANCE = it }
//            }
//        }
//    }
//}


class DriveServiceHelper(private val driveService: Drive) {

        fun uploadBitmap(bitmap: Bitmap, fileName: String): String? {
            return try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val byteArray = outputStream.toByteArray()

                val metadata = File().apply {
                    name = fileName
                    mimeType = "image/jpeg"
                    // --- REDIRECT TO YOUR TESTING FOLDER ---
                    parents = listOf("1S9ZbLpwMfwjweUIfhEduiR0eA_-b7I1u")
                }

                val mediaContent = ByteArrayContent("image/jpeg", byteArray)

                val googleFile = driveService.files().create(metadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute()

                // --- AUTOMATICALLY SHARE WITH YOU (THE PARENT) ---
                // This ensures that even if it's in a shared folder,
                // the link in Firestore is viewable.
                val permission = Permission()
                    .setType("anyone")
                    .setRole("reader")

                driveService.permissions().create(googleFile.id, permission).execute()

                Log.d("DriveServiceHelper", "Uploaded to Shared Testing Folder: ${googleFile.webViewLink}")
                googleFile.webViewLink
            } catch (e: Exception) {
                Log.e("DriveServiceHelper", "Upload failed", e)
                null
            }
        }
//    fun uploadBitmap(bitmap: Bitmap, fileName: String): String? {
//        return try {
//            val outputStream = ByteArrayOutputStream()
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
//            val byteArray = outputStream.toByteArray()
//
//            // 1. Metadata for the file
//            val metadata = File().apply {
//                name = fileName
//                mimeType = "image/jpeg"
//                // Optional: parents = Collections.singletonList("YOUR_FOLDER_ID")
//            }
//
//            // 2. The actual Upload
//            val mediaContent = ByteArrayContent("image/jpeg", byteArray)
//            val googleFile = driveService.files().create(metadata, mediaContent)
//                .setFields("id, webViewLink")
//                .execute()
//
//            Log.d("DriveServiceHelper", "Real upload success: ${googleFile.webViewLink}")
//            googleFile.webViewLink // Returns the link for Firestore
//        } catch (e: Exception) {
//            Log.e("DriveServiceHelper", "Real Drive upload failed", e)
//            null
//        }
//    }
}