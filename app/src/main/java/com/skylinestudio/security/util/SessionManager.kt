package com.skylinestudio.security.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "tortoise_session"
        private const val KEY_UID = "user_uid"
        private const val KEY_ROLE = "user_role"
    }

    fun saveSession(uid: String, role: String) {
        prefs.edit().apply {
            putString(KEY_UID, uid)
            putString(KEY_ROLE, role)
            apply()
        }
    }

    fun getUid(): String? = prefs.getString(KEY_UID, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getUid() != null && getRole() != null
}
