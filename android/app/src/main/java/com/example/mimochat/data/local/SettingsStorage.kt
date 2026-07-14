package com.example.mimochat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mimochat.data.AuthMode
import com.example.mimochat.data.MimoConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 安全配置存储 - 使用 EncryptedSharedPreferences
 * API Key 等敏感信息加密存储
 */
class SettingsStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mimo_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mimo_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    // ── Connection (secure) ──

    fun saveConnection(connection: MimoConnection) {
        securePrefs.edit()
            .putString(KEY_BASE_URL, connection.baseUrl)
            .putString(KEY_API_KEY, connection.apiKey)
            .putString(KEY_AUTH_MODE, connection.authMode.name)
            .apply()
    }

    fun loadConnection(): MimoConnection {
        return MimoConnection(
            baseUrl = securePrefs.getString(KEY_BASE_URL, "https://api.xiaomimimo.com/v1") ?: "https://api.xiaomimimo.com/v1",
            apiKey = securePrefs.getString(KEY_API_KEY, "") ?: "",
            authMode = try {
                AuthMode.valueOf(securePrefs.getString(KEY_AUTH_MODE, "API_KEY") ?: "API_KEY")
            } catch (_: Exception) { AuthMode.API_KEY }
        )
    }

    fun clearApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
    }

    fun hasApiKey(): Boolean = !securePrefs.getString(KEY_API_KEY, "").isNullOrBlank()

    // ── Preferences (non-secure) ──

    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) = prefs.edit().putString("theme", value).apply()

    var defaultRoleId: String
        get() = prefs.getString("default_role", "mimo") ?: "mimo"
        set(value) = prefs.edit().putString("default_role", value).apply()

    var rolesJson: String
        get() = prefs.getString("roles", "") ?: ""
        set(value) = prefs.edit().putString("roles", value).apply()

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AUTH_MODE = "auth_mode"
    }
}
