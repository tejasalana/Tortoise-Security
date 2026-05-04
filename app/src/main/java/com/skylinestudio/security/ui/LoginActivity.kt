package com.skylinestudio.security.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.skylinestudio.security.ui.screens.AuthScreen
import com.skylinestudio.security.ui.screens.RoleSelectionScreen
import com.skylinestudio.security.ui.theme.TortoiseSecurityTheme
import com.skylinestudio.security.ui.viewmodel.AuthViewModel
import com.skylinestudio.security.util.SessionManager

class LoginActivity : ComponentActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseApp.initializeApp(this)
        sessionManager = SessionManager(this)

        // Redirect to MainActivity if already logged in
        if (sessionManager.isLoggedIn() && FirebaseAuth.getInstance().currentUser != null) {
            startMainActivity()
            return
        }

        setContent {
            TortoiseSecurityTheme {
                // Task 2: Fix Screen Navigation State (Orientation Change)
                // Use rememberSaveable to retain the selected role across configuration changes
                var selectedRole by rememberSaveable { mutableStateOf<String?>(null) }
                val authViewModel: AuthViewModel = viewModel()

                if (selectedRole == null) {
                    RoleSelectionScreen(
                        onRoleSelected = { role ->
                            selectedRole = role
                        }
                    )
                } else {
                    // Task 1: AuthScreen contains the Google Sign-In logic and button
                    AuthScreen(
                        role = selectedRole!!,
                        authViewModel = authViewModel,
                        sessionManager = sessionManager,
                        onAuthSuccess = {
                            startMainActivity()
                        },
                        onBack = {
                            selectedRole = null
                        }
                    )
                }
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}