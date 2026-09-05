package ru.merrcurys.siphone.data.repositories

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    private val legacyAuthPrefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SIP_ID = "sip_id"
        private const val KEY_SIP_PASSWORD = "sip_password"
        private const val KEY_MOCK_SERVER = "mock_server"
    }

    fun isMockServer(): Boolean = prefs.getBoolean(KEY_MOCK_SERVER, false)

    fun setMockServer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOCK_SERVER, enabled).apply()
    }

    fun getThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getServerIp(): String? {
        return prefs.getString(KEY_SERVER_IP, null)
            ?: legacyAuthPrefs.getString("server_ip", null)
    }

    fun saveServerIp(ip: String) {
        prefs.edit().putString(KEY_SERVER_IP, ip).apply()
    }

    fun getSipId(): String? {
        return prefs.getString(KEY_SIP_ID, null)
    }

    fun saveSipId(sipId: String) {
        prefs.edit().putString(KEY_SIP_ID, sipId).apply()
    }

    fun getSipPassword(): String? {
        return prefs.getString(KEY_SIP_PASSWORD, null)
    }

    fun saveSipPassword(sipPassword: String) {
        prefs.edit().putString(KEY_SIP_PASSWORD, sipPassword).apply()
    }
}
