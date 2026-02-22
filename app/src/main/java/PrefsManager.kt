package com.example.SafeLYN

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    // Using a new Prefs name forces the app to ignore all old, stuck data
    private val PREFS_NAME = "SafeLYN_V2_Storage"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // UNIFIED V2 KEYS
    private val KEY_NAMES = "V2_GUARDIAN_NAMES"
    private val KEY_NUMBERS = "V2_GUARDIAN_NUMBERS"
    private val KEY_VOICE = "V2_VOICE_KEYWORD"
    private val KEY_GESTURE = "V2_SELECTED_GESTURE"

    fun saveGuardianList(names: List<String>, numbers: List<String>) {
        prefs.edit()
            .putString(KEY_NAMES, names.joinToString(","))
            .putString(KEY_NUMBERS, numbers.joinToString(","))
            .apply()
    }

    fun getGuardianNumbers(): List<String> {
        val raw = prefs.getString(KEY_NUMBERS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").map { it.trim() }
    }

    fun getGuardianNamesList(): List<String> {
        val raw = prefs.getString(KEY_NAMES, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").map { it.trim() }
    }

    fun getGuardianNames(): String {
        val list = getGuardianNamesList()
        return if (list.isEmpty()) "Emergency Contacts" else list.joinToString(", ")
    }

    // Settings
    fun saveVoiceKeyword(k: String) = prefs.edit().putString(KEY_VOICE, k).apply()
    fun getVoiceKeyword(): String = prefs.getString(KEY_VOICE, "Help") ?: "Help"
    fun saveSelectedGesture(g: String) = prefs.edit().putString(KEY_GESTURE, g).apply()
    fun getSelectedGesture(): String = prefs.getString(KEY_GESTURE, "Closed_Fist") ?: "Closed_Fist"
}