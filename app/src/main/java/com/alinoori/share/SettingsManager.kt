package com.alinoori.share

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val deviceName: String,
    val language: String,          // "fa" or "en"
    val theme: ThemeMode,
    val saveTreeUri: String?,      // SAF tree chosen by the user, null = app folder
    val notifyOnReceive: Boolean,
    val confirmNewDevice: Boolean,
    val onboarded: Boolean
)

class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state

    private fun load(): Settings = Settings(
        deviceName = prefs.getString("deviceName", null) ?: (Build.MODEL ?: "Android"),
        language = prefs.getString("language", null) ?: "fa",
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        saveTreeUri = prefs.getString("saveTreeUri", null),
        notifyOnReceive = prefs.getBoolean("notify", true),
        confirmNewDevice = prefs.getBoolean("confirm", true),
        onboarded = prefs.getBoolean("onboarded", false)
    )

    private fun commit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    fun setDeviceName(v: String) = commit { putString("deviceName", v.trim().ifEmpty { Build.MODEL ?: "Android" }) }
    fun setTheme(v: ThemeMode) = commit { putString("theme", v.name) }
    fun setSaveTree(uri: Uri?) = commit { if (uri == null) remove("saveTreeUri") else putString("saveTreeUri", uri.toString()) }
    fun setNotify(v: Boolean) = commit { putBoolean("notify", v) }
    fun setConfirm(v: Boolean) = commit { putBoolean("confirm", v) }
    fun setOnboarded() = commit { putBoolean("onboarded", true) }

    fun setLanguage(tag: String) {
        commit { putString("language", tag) }
        applyLanguage()
    }

    /** Applies the saved language using AppCompat per-app locales (works on API 24+). */
    fun applyLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(_state.value.language))
    }
}
