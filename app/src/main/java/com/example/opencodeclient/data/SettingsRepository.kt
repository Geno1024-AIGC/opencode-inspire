package com.example.opencodeclient.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val PROJECT_PATH = stringPreferencesKey("project_path")
        val LAST_SESSION = stringPreferencesKey("last_session_id")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val projectPath: Flow<String?> = context.dataStore.data.map { it[Keys.PROJECT_PATH] }
    val authUsername: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_USERNAME] }
    val authPassword: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_PASSWORD] }

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

    suspend fun getLastSessionId(): String? =
        context.dataStore.data.first()[Keys.LAST_SESSION]

    suspend fun setLastSessionId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.LAST_SESSION)
            else it[Keys.LAST_SESSION] = id
        }
    }
}
