package com.skylinestudio.security.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skylinestudio.security.service.MainMonitoringService
import com.skylinestudio.security.service.TortoiseAccessibilityService
import com.skylinestudio.security.ui.screens.AuthScreen
import com.skylinestudio.security.ui.screens.ChildHomeScreen
import com.skylinestudio.security.ui.screens.ParentDashboardScreen
import com.skylinestudio.security.ui.screens.RoleSelectionScreen
import com.skylinestudio.security.ui.viewmodel.AuthViewModel
import com.skylinestudio.security.ui.viewmodel.ParentDashboardViewModel
import com.skylinestudio.security.util.SessionManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.skylinestudio.security.ui.theme.TortoiseSecurityTheme

sealed class Screen {
    object Loading : Screen()
    object RoleSelection : Screen()
    data class Auth(val role: String) : Screen()
    object ParentDashboard : Screen()
    object ChildHome : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private var isCheckingPermissions = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
            checkAndRequestPermissions()
        } else {
            Log.w("MainActivity", "Notification permission denied")
            Toast.makeText(this, "Notification permission is required for background monitoring", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sessionManager = SessionManager(this)

        FirebaseApp.initializeApp(this)
        val db = Firebase.firestore
        db.collection("test").document("connection").set(mapOf("status" to "success"))
            .addOnSuccessListener { Log.d("FirebaseTest", "Connected Successfully!") }
            .addOnFailureListener { e -> Log.e("FirebaseTest", "Error: ", e) }

        setContent {
            TortoiseSecurityTheme {
                TortoiseSecurityApp(
                    sessionManager = sessionManager,
                    onStartServiceRequested = { checkAndRequestPermissions() },
                    onExitApp = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isCheckingPermissions) {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        if (sessionManager.getRole() != "CHILD") return

        isCheckingPermissions = true

        when {
            !hasNotificationPermission() -> {
                requestNotificationPermission()
            }
            !isUsageStatsPermissionGranted() -> {
                Toast.makeText(this, "Please enable Usage Stats for app tracking", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            !isAccessibilityServiceEnabled() -> {
                Toast.makeText(this, "Please enable Tortoise Accessibility Service", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            else -> {
                isCheckingPermissions = false
                startMonitoringService()
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, TortoiseAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName.flattenToString()) == true
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MainMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun stopMonitoringService() {
        val intent = Intent(this, MainMonitoringService::class.java)
        stopService(intent)
    }
}

@Composable
private fun TortoiseSecurityApp(
    sessionManager: SessionManager,
    onStartServiceRequested: () -> Unit,
    onExitApp: () -> Unit
) {
    // Custom Saver to retain navigation state across orientation changes
    val screenSaver = remember {
        Saver<Screen, String>(
            save = {
                when (it) {
                    is Screen.Loading -> "loading"
                    is Screen.RoleSelection -> "role_selection"
                    is Screen.Auth -> "auth:${it.role}"
                    is Screen.ParentDashboard -> "parent_dashboard"
                    is Screen.ChildHome -> "child_home"
                }
            },
            restore = {
                when {
                    it == "loading" -> Screen.Loading
                    it == "role_selection" -> Screen.RoleSelection
                    it.startsWith("auth:") -> Screen.Auth(it.substringAfter("auth:"))
                    it == "parent_dashboard" -> Screen.ParentDashboard
                    it == "child_home" -> Screen.ChildHome
                    else -> Screen.RoleSelection
                }
            }
        )
    }

    var currentScreen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf(Screen.Loading)
    }
    
    var showExitDialog by remember { mutableStateOf(false) }
    val authViewModel: AuthViewModel = viewModel()
    val dashboardViewModel: ParentDashboardViewModel = viewModel()

    val logout = {
        Firebase.auth.signOut()
        sessionManager.clearSession()
        currentScreen = Screen.RoleSelection
    }

    LaunchedEffect(Unit) {
        if (sessionManager.isLoggedIn()) {
            val role = sessionManager.getRole()
            currentScreen = if (role == "CHILD") {
                onStartServiceRequested()
                Screen.ChildHome
            } else {
                Screen.ParentDashboard
            }
        } else {
            // Restore RoleSelection if we are stuck on Loading
            if (currentScreen == Screen.Loading) {
                currentScreen = Screen.RoleSelection
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = onExitApp) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            }
        )
    }

    when (val screen = currentScreen) {
        is Screen.Loading -> { /* Splash screen */ }
        is Screen.RoleSelection -> RoleSelectionScreen(
            onRoleSelected = { role -> currentScreen = Screen.Auth(role) },
        )
        is Screen.Auth -> AuthScreen(
            role = screen.role,
            authViewModel = authViewModel,
            onAuthSuccess = {
                if (screen.role == "CHILD") {
                    currentScreen = Screen.ChildHome
                    onStartServiceRequested()
                } else {
                    currentScreen = Screen.ParentDashboard
                }
            },
            onBack = { currentScreen = Screen.RoleSelection },
            sessionManager = sessionManager
        )
        is Screen.ParentDashboard -> {
            BackHandler { showExitDialog = true }
            ParentDashboardScreen(
                viewModel = dashboardViewModel,
                onLogout = logout
            )
        }
        is Screen.ChildHome -> {
            BackHandler { showExitDialog = true }
            ChildHomeScreen(onLogout = logout)
        }
    }
}
