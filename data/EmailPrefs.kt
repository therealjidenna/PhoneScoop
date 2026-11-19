package com.jidenna.activitylog.data

import android.content.Context

object EmailPrefs {
    private const val PREFS_NAME = "email_prefs"
    private const val KEY_USER_EMAIL = "user_email"

    fun getUserEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null)?.takeIf { it.isNotBlank() }
    }

    fun setUserEmail(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_EMAIL, email.trim()).apply()
    }
}
