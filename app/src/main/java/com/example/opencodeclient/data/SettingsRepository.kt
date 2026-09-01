package com.example.opencodeclient.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val PROJECT_PATH = stringPreferencesKey("project_path")
        val LAST_SESSION = stringPreferencesKey("last_session_id")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
        val SERVERS = stringPreferencesKey("servers")
        val SHORT_TOKENS = booleanPreferencesKey("short_tokens")
        val THEME = stringPreferencesKey("theme")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_THEME_COLORS = stringPreferencesKey("custom_theme_colors")
        val LANGUAGE = stringPreferencesKey("language")
        val CHANNEL = stringPreferencesKey("channel")
        val USER_BUBBLE_COLOR = longPreferencesKey("user_bubble_color")
        val ASSIST_BUBBLE_COLOR = longPreferencesKey("assistant_bubble_color")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val projectPath: Flow<String?> = context.dataStore.data.map { it[Keys.PROJECT_PATH] }
    val authUsername: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_USERNAME] }
    val authPassword: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_PASSWORD] }

    val servers: Flow<List<ServerProfile>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVERS]?.let { raw ->
            runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val shortTokens: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHORT_TOKENS] ?: true }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val themePreset: Flow<String> = context.dataStore.data.map { it[Keys.THEME_PRESET] ?: "default" }
    val customThemeColors: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_THEME_COLORS] ?: "{}" }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "system" }
    val channel: Flow<String> = context.dataStore.data.map { it[Keys.CHANNEL] ?: "release" }
    val userBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.USER_BUBBLE_COLOR] ?: -1L }
    val assistantBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.ASSIST_BUBBLE_COLOR] ?: -1L }

    suspend fun setShortTokens(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHORT_TOKENS] = enabled }
    }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setThemePreset(value: String) {
        context.dataStore.edit { it[Keys.THEME_PRESET] = value }
    }

    suspend fun setCustomThemeColors(json: String) {
        context.dataStore.edit { it[Keys.CUSTOM_THEME_COLORS] = json }
    }

    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = value }
    }

    suspend fun setChannel(value: String) {
        context.dataStore.edit { it[Keys.CHANNEL] = value }
    }

    suspend fun setUserBubbleColor(color: Long) {
        context.dataStore.edit { it[Keys.USER_BUBBLE_COLOR] = color }
    }

    suspend fun setAssistantBubbleColor(color: Long) {
        context.dataStore.edit { it[Keys.ASSIST_BUBBLE_COLOR] = color }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setProjectPath(path: String) {
        context.dataStore.edit { it[Keys.PROJECT_PATH] = path }
    }

    suspend fun setAuth(username: String?, password: String?) {
        context.dataStore.edit {
            if (username.isNullOrEmpty()) it.remove(Keys.AUTH_USERNAME)
            else it[Keys.AUTH_USERNAME] = username
            if (password.isNullOrEmpty()) it.remove(Keys.AUTH_PASSWORD)
            else it[Keys.AUTH_PASSWORD] = password
        }
    }

    suspend fun saveServer(profile: ServerProfile) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrNull()
            } ?: emptyList()
            val updated = current.filterNot { it.url == profile.url } + profile
            prefs[Keys.SERVERS] = json.encodeToString(ListSerializer(ServerProfile.serializer()), updated)
        }
    }

    suspend fun removeServer(url: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrNull()
            } ?: emptyList()
            prefs[Keys.SERVERS] = json.encodeToString(
                ListSerializer(ServerProfile.serializer()),
                current.filterNot { it.url == url },
            )
        }
    }

    suspend fun getLastSessionId(): String? =
        context.dataStore.data.first()[Keys.LAST_SESSION]

    suspend fun setLastSessionId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.LAST_SESSION)
            else it[Keys.LAST_SESSION] = id
        }
    }
}
