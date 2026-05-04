package com.skylinestudio.security.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skylinestudio.security.data.User
import com.skylinestudio.security.repo.FirebaseRepo
import com.skylinestudio.security.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGNUP }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val parentId: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, errorMessage = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, errorMessage = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onParentIdChange(v: String) = _state.update { it.copy(parentId = v, errorMessage = null) }

    fun toggleAuthMode() = _state.update {
        it.copy(
            authMode = if (it.authMode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN,
            errorMessage = null,
        )
    }

    fun onGoogleSignInResult(idToken: String, role: String, sessionManager: SessionManager, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (role == "CHILD" && _state.value.authMode == AuthMode.SIGNUP) {
                    if (_state.value.parentId.isBlank()) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Please enter Parent ID first for child registration.") }
                        return@launch
                    }
                    val valid = FirebaseRepo.verifyParentId(_state.value.parentId)
                    if (!valid) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Invalid Parent ID.") }
                        return@launch
                    }
                }

                val uid = FirebaseRepo.signInWithGoogle(idToken)
                var user = FirebaseRepo.getUser(uid)

                if (user == null) {
                    // New user signing in with Google
                    user = User(
                        uid = uid,
                        name = "Google User",
                        email = "", 
                        role = role,
                        parentId = if (role == "CHILD") _state.value.parentId else null
                    )
                    FirebaseRepo.saveUser(user)
                }

                // user is definitely non-null here
                sessionManager.saveSession(uid, user.role)
                _state.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onSubmit(role: String, sessionManager: SessionManager, onSuccess: () -> Unit) {
        val s = _state.value

        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Email and password are required.") }
            return
        }
        if (s.authMode == AuthMode.SIGNUP && s.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val uid: String
                if (s.authMode == AuthMode.SIGNUP) {
                    if (role == "CHILD") {
                        if (s.parentId.isBlank()) {
                            _state.update { it.copy(isLoading = false, errorMessage = "Parent ID is required.") }
                            return@launch
                        }
                        val valid = FirebaseRepo.verifyParentId(s.parentId)
                        if (!valid) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Invalid Parent ID. Double-check with your parent.",
                                )
                            }
                            return@launch
                        }
                    }

                    uid = FirebaseRepo.signUpWithEmail(s.email, s.password)

                    FirebaseRepo.saveUser(
                        User(
                            uid = uid,
                            name = s.name,
                            email = s.email,
                            role = role,
                            parentId = if (role == "CHILD") s.parentId else null,
                        )
                    )
                } else {
                    uid = FirebaseRepo.signInWithEmail(s.email, s.password)
                }

                val userInDb = FirebaseRepo.getUser(uid)
                val finalRole = userInDb?.role ?: role
                sessionManager.saveSession(uid, finalRole)
                _state.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = friendlyError(e.message))
                }
            }
        }
    }

    private fun friendlyError(raw: String?): String = when {
        raw == null -> "Authentication failed. Please try again."
        "email address is already in use" in raw -> "This email is already registered. Try signing in."
        "password is invalid" in raw || "wrong-password" in raw -> "Incorrect password."
        "no user record" in raw || "user-not-found" in raw -> "No account found with this email."
        "badly formatted" in raw -> "Please enter a valid email address."
        "network" in raw.lowercase() -> "Network error. Check your connection and try again."
        else -> raw
    }
}
