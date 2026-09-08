package com.geno1024.ai.occ.data

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
import kotlinx.serialization.Serializable

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class StoredHistoryStats(
    val totalElapsed: Long = 0L,
    val messageCount: Long = 0L,
    val userMessages: Long = 0L,
    val assistantMessages: Long = 0L,
    val toolCalls: Long = 0L,
    val firstMessages: List<String> = emptyList(),
    val lastTimestamp: Long = 0L,
    val lastMessageId: String = "",
)

@Serializable
data class SettingsBackup(
    val serverUrl: String? = null,
    val projectPath: String? = null,
    val lastSessionId: String? = null,
    val tokenFormat: String = TokenFormat.DEFAULT.id,
    val theme: String = "system",
    val themePreset: String = "default",
    val customThemeColors: String = "{}",
    val language: String = "system",
    val channel: String = "release",
    val mirror: Boolean = false,
    val userBubbleColor: Long = -1L,
    val assistantBubbleColor: Long = -1L,
    val draftTitles: Long = -1L,
    val autoUpdateTiming: Boolean = false,
    val tableTimeFormat: Int = 0,
    val exportTransparent: Boolean = true,
    val exportAuthor: String = "",
    val servers: List<ServerProfile> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    val drafts: Map<String, String> = emptyMap(),
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val PROJECT_PATH = stringPreferencesKey("project_path")
        val LAST_SESSION = stringPreferencesKey("last_session_id")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
        val SERVERS = stringPreferencesKey("servers")
        val SHORT_TOKENS = booleanPreferencesKey("short_tokens")
        val TOKEN_FORMAT = stringPreferencesKey("token_format")
        val THEME = stringPreferencesKey("theme")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_THEME_COLORS = stringPreferencesKey("custom_theme_colors")
        val LANGUAGE = stringPreferencesKey("language")
        val CHANNEL = stringPreferencesKey("channel")
        val MIRROR = booleanPreferencesKey("mirror")
        val USER_BUBBLE_COLOR = longPreferencesKey("user_bubble_color")
        val ASSIST_BUBBLE_COLOR = longPreferencesKey("assistant_bubble_color")
        val AUTO_UPDATE_TIMING = booleanPreferencesKey("auto_update_timing")
        val TABLE_TIME_FORMAT = intPreferencesKey("table_time_format")
        val EXPORT_TRANSPARENT = booleanPreferencesKey("export_transparent")
        val EXPORT_AUTHOR = stringPreferencesKey("export_author")
        val FAVORITES = stringPreferencesKey("favorites")
        val ARCHIVED = stringPreferencesKey("archived")
        val IGNORED_PERMISSIONS = stringPreferencesKey("ignored_permissions")
        val IGNORED_QUESTIONS = stringPreferencesKey("ignored_questions")
        val HISTORY_STATS = stringPreferencesKey("history_stats")
        val TOKEN_HISTORY = stringPreferencesKey("token_history")
        val TOKEN_ELAPSED = stringPreferencesKey("token_elapsed")
        val TOKEN_MONTH = stringPreferencesKey("token_month")
        val TOKEN_WEEK = stringPreferencesKey("token_week")
        val TOKEN_DAY_HOURS = stringPreferencesKey("token_day_hours")
        val TOKEN_MODEL_STATS = stringPreferencesKey("token_model_stats")
        val SESSION_MODEL_TOKENS = stringPreferencesKey("session_model_tokens")
        val TOKEN_SYNC = longPreferencesKey("token_sync")
        val TOKEN_SYNC_AT = longPreferencesKey("token_sync_at")
        val DOWNLOADED_APK = stringPreferencesKey("downloaded_apk")
        val INSTALLED_VERSION = stringPreferencesKey("installed_version")
        val DRAFTS = stringPreferencesKey("drafts")
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

    val tokenFormat: Flow<TokenFormat> = context.dataStore.data.map { p ->
        p[Keys.TOKEN_FORMAT]?.let { TokenFormat.fromId(it) }
            ?: p[Keys.SHORT_TOKENS]?.let { if (it) TokenFormat.DEFAULT else TokenFormat.RAW }
            ?: TokenFormat.DEFAULT
    }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val themePreset: Flow<String> = context.dataStore.data.map { it[Keys.THEME_PRESET] ?: "default" }
    val customThemeColors: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_THEME_COLORS] ?: "{}" }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "system" }
    val channel: Flow<String> = context.dataStore.data.map { it[Keys.CHANNEL] ?: "release" }
    val mirror: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIRROR] ?: false }
    val userBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.USER_BUBBLE_COLOR] ?: -1L }
    val assistantBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.ASSIST_BUBBLE_COLOR] ?: -1L }
    val autoUpdateTiming: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE_TIMING] ?: false }
    val tableTimeFormat: Flow<Int> = context.dataStore.data.map { it[Keys.TABLE_TIME_FORMAT] ?: 0 }
    val exportTransparent: Flow<Boolean> = context.dataStore.data.map { it[Keys.EXPORT_TRANSPARENT] ?: true }
    val exportAuthor: Flow<String> = context.dataStore.data.map { it[Keys.EXPORT_AUTHOR] ?: "" }
    val favorites: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITES]?.let { raw ->
            runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
        } ?: emptySet()
    }
    val archived: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.ARCHIVED]?.let { raw ->
            runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
        } ?: emptySet()
    }
    val ignoredPermissions: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.IGNORED_PERMISSIONS]?.let { raw ->
            runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
        } ?: emptySet()
    }
    val ignoredQuestions: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.IGNORED_QUESTIONS]?.let { raw ->
            runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
        } ?: emptySet()
    }
    val historyStats: Flow<Map<String, StoredHistoryStats>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.HISTORY_STATS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, StoredHistoryStats>>(raw) }.getOrNull()
            } ?: emptyMap()
        }
    val tokenHistory: Flow<Map<String, TokenDay>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_HISTORY]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, TokenDay>>(raw) }.getOrElse {
                runCatching { json.decodeFromString<Map<String, Long>>(raw) }
                    .getOrDefault(emptyMap())
                    .mapValues { (_, v) -> TokenDay(total = v) }
            }
        } ?: emptyMap()
    }
    val tokenElapsed: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_ELAPSED]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrNull()
        } ?: emptyMap()
    }
    val tokenMonth: Flow<Map<String, Map<Int, TokenDay>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_MONTH]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(raw) }.getOrElse {
                runCatching {
                    json.decodeFromString<Map<String, Map<Int, Long>>>(raw).mapValues { (_, m) ->
                        m.mapValues { (_, v) -> TokenDay(total = v) }
                    }
                }.getOrNull()
            }
        } ?: emptyMap()
    }
    val tokenWeek: Flow<Map<String, Map<Int, TokenDay>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_WEEK]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(raw) }.getOrElse {
                runCatching {
                    json.decodeFromString<Map<String, Map<Int, Long>>>(raw).mapValues { (_, m) ->
                        m.mapValues { (_, v) -> TokenDay(total = v) }
                    }
                }.getOrNull()
            }
        } ?: emptyMap()
    }
    val tokenDayHours: Flow<Map<String, Map<Int, TokenDay>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_DAY_HOURS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(raw) }.getOrElse {
                runCatching {
                    json.decodeFromString<Map<String, Map<Int, Long>>>(raw).mapValues { (_, m) ->
                        m.mapValues { (_, v) -> TokenDay(total = v) }
                    }
                }.getOrNull()
            }
        } ?: emptyMap()
    }
    val tokenSync: Flow<Long> = context.dataStore.data.map { it[Keys.TOKEN_SYNC] ?: 0L }
    val tokenSyncedAt: Flow<Long> = context.dataStore.data.map { it[Keys.TOKEN_SYNC_AT] ?: 0L }
    val downloadedApk: Flow<String?> = context.dataStore.data.map { it[Keys.DOWNLOADED_APK] }
    val installedVersion: Flow<String?> = context.dataStore.data.map { it[Keys.INSTALLED_VERSION] }
    val drafts: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DRAFTS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
        } ?: emptyMap()
    }
    val tokenModelStats: Flow<Map<String, TokenModelStats>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_MODEL_STATS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, TokenModelStats>>(raw) }.getOrNull()
        } ?: emptyMap()
    }
    val sessionModelTokens: Flow<Map<String, Map<String, TokenDay>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SESSION_MODEL_TOKENS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Map<String, TokenDay>>>(raw) }.getOrNull()
        } ?: emptyMap()
    }

    suspend fun setTokenFormat(format: TokenFormat) {
        context.dataStore.edit { it[Keys.TOKEN_FORMAT] = format.id }
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

    suspend fun setMirror(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MIRROR] = enabled }
    }

    suspend fun setUserBubbleColor(color: Long) {
        context.dataStore.edit { it[Keys.USER_BUBBLE_COLOR] = color }
    }

    suspend fun setAssistantBubbleColor(color: Long) {
        context.dataStore.edit { it[Keys.ASSIST_BUBBLE_COLOR] = color }
    }

    suspend fun setAutoUpdateTiming(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_TIMING] = enabled }
    }

    suspend fun setTableTimeFormat(v: Int) {
        context.dataStore.edit { it[Keys.TABLE_TIME_FORMAT] = v }
    }

    suspend fun setExportTransparent(v: Boolean) {
        context.dataStore.edit { it[Keys.EXPORT_TRANSPARENT] = v }
    }

    suspend fun setExportAuthor(v: String) {
        context.dataStore.edit { it[Keys.EXPORT_AUTHOR] = v }
    }

    suspend fun toggleFavorite(sessionId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES]?.let { raw ->
                runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
            } ?: emptySet()
            prefs[Keys.FAVORITES] = json.encodeToString(
                if (sessionId in current) current - sessionId else current + sessionId
            )
        }
    }

    suspend fun toggleArchived(sessionId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ARCHIVED]?.let { raw ->
                runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
            } ?: emptySet()
            prefs[Keys.ARCHIVED] = json.encodeToString(
                if (sessionId in current) current - sessionId else current + sessionId
            )
        }
    }

    private suspend fun updateIgnoredSet(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String,
        add: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { raw ->
                runCatching { json.decodeFromString<Set<String>>(raw) }.getOrNull()
            } ?: emptySet()
            prefs[key] = json.encodeToString(
                if (add) current + value else current - value
            )
        }
    }

    suspend fun addIgnoredPermission(key: String) =
        updateIgnoredSet(Keys.IGNORED_PERMISSIONS, key, true)

    suspend fun removeIgnoredPermission(key: String) =
        updateIgnoredSet(Keys.IGNORED_PERMISSIONS, key, false)

    suspend fun addIgnoredQuestion(key: String) =
        updateIgnoredSet(Keys.IGNORED_QUESTIONS, key, true)

    suspend fun removeIgnoredQuestion(key: String) =
        updateIgnoredSet(Keys.IGNORED_QUESTIONS, key, false)

    suspend fun saveTokenHistory(tokens: Map<String, TokenDay>, elapsed: Map<String, Long>) {
        context.dataStore.edit {
            it[Keys.TOKEN_HISTORY] = json.encodeToString(tokens)
            it[Keys.TOKEN_ELAPSED] = json.encodeToString(elapsed)
        }
    }

    suspend fun saveTokenCalendar(
        month: Map<String, Map<Int, TokenDay>>,
        week: Map<String, Map<Int, TokenDay>>,
        day: Map<String, Map<Int, TokenDay>>,
        syncMs: Long,
        syncedAtMs: Long = 0L,
    ) {
        context.dataStore.edit {
            it[Keys.TOKEN_MONTH] = json.encodeToString(month)
            it[Keys.TOKEN_WEEK] = json.encodeToString(week)
            it[Keys.TOKEN_DAY_HOURS] = json.encodeToString(day)
            it[Keys.TOKEN_SYNC] = syncMs
            it[Keys.TOKEN_SYNC_AT] = syncedAtMs
        }
    }

    suspend fun saveTokenModelStats(stats: Map<String, TokenModelStats>) {
        context.dataStore.edit { it[Keys.TOKEN_MODEL_STATS] = json.encodeToString(stats) }
    }

    suspend fun saveSessionModelTokens(stats: Map<String, Map<String, TokenDay>>) {
        context.dataStore.edit { it[Keys.SESSION_MODEL_TOKENS] = json.encodeToString(stats) }
    }

    suspend fun saveHistoryStats(sessionId: String, stats: StoredHistoryStats) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HISTORY_STATS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, StoredHistoryStats>>(raw) }.getOrNull()
            } ?: emptyMap()
            prefs[Keys.HISTORY_STATS] = json.encodeToString(current + (sessionId to stats))
        }
    }

    suspend fun removeHistoryStats(sessionId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HISTORY_STATS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, StoredHistoryStats>>(raw) }.getOrNull()
            } ?: emptyMap()
            if (current.containsKey(sessionId)) {
                prefs[Keys.HISTORY_STATS] = json.encodeToString(current - sessionId)
            }
        }
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

    suspend fun setDownloadedApk(name: String?) {
        context.dataStore.edit { prefs ->
            if (name == null) prefs.remove(Keys.DOWNLOADED_APK)
            else prefs[Keys.DOWNLOADED_APK] = name
        }
    }

    suspend fun setInstalledVersion(version: String?) {
        context.dataStore.edit { prefs ->
            if (version == null) prefs.remove(Keys.INSTALLED_VERSION)
            else prefs[Keys.INSTALLED_VERSION] = version
        }
    }

    suspend fun setDrafts(drafts: Map<String, String>) {
        context.dataStore.edit { it[Keys.DRAFTS] = json.encodeToString(drafts) }
    }

    suspend fun backupSettings(): SettingsBackup {
        val p = context.dataStore.data.first()
        fun setOf(raw: String?): Set<String> = raw?.let { r ->
            runCatching { json.decodeFromString<Set<String>>(r) }.getOrNull()
        } ?: emptySet()
        return SettingsBackup(
            serverUrl = p[Keys.SERVER_URL],
            projectPath = p[Keys.PROJECT_PATH],
            lastSessionId = p[Keys.LAST_SESSION],
            tokenFormat = p[Keys.TOKEN_FORMAT]
                ?: p[Keys.SHORT_TOKENS]?.let { if (it) TokenFormat.DEFAULT.id else TokenFormat.RAW.id }
                ?: TokenFormat.DEFAULT.id,
            theme = p[Keys.THEME] ?: "system",
            themePreset = p[Keys.THEME_PRESET] ?: "default",
            customThemeColors = p[Keys.CUSTOM_THEME_COLORS] ?: "{}",
            language = p[Keys.LANGUAGE] ?: "system",
            channel = p[Keys.CHANNEL] ?: "release",
            mirror = p[Keys.MIRROR] ?: false,
            userBubbleColor = p[Keys.USER_BUBBLE_COLOR] ?: -1L,
            assistantBubbleColor = p[Keys.ASSIST_BUBBLE_COLOR] ?: -1L,
            autoUpdateTiming = p[Keys.AUTO_UPDATE_TIMING] ?: false,
            tableTimeFormat = p[Keys.TABLE_TIME_FORMAT] ?: 0,
            exportTransparent = p[Keys.EXPORT_TRANSPARENT] ?: true,
            exportAuthor = p[Keys.EXPORT_AUTHOR] ?: "",
            servers = p[Keys.SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrNull()
            } ?: emptyList(),
            favorites = setOf(p[Keys.FAVORITES]),
            archived = setOf(p[Keys.ARCHIVED]),
            drafts = p[Keys.DRAFTS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
            } ?: emptyMap(),
        )
    }

    suspend fun restoreSettings(b: SettingsBackup) {
        context.dataStore.edit { p ->
            fun setOrRemove(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String?) {
                if (value == null) p.remove(key) else p[key] = value
            }
            setOrRemove(Keys.SERVER_URL, b.serverUrl)
            setOrRemove(Keys.PROJECT_PATH, b.projectPath)
            setOrRemove(Keys.LAST_SESSION, b.lastSessionId)
            p[Keys.TOKEN_FORMAT] = b.tokenFormat
            p[Keys.THEME] = b.theme
            p[Keys.THEME_PRESET] = b.themePreset
            p[Keys.CUSTOM_THEME_COLORS] = b.customThemeColors
            p[Keys.LANGUAGE] = b.language
            p[Keys.CHANNEL] = b.channel
            p[Keys.MIRROR] = b.mirror
            p[Keys.TABLE_TIME_FORMAT] = b.tableTimeFormat
            p[Keys.EXPORT_TRANSPARENT] = b.exportTransparent
            p[Keys.EXPORT_AUTHOR] = b.exportAuthor
            p[Keys.USER_BUBBLE_COLOR] = b.userBubbleColor
            p[Keys.ASSIST_BUBBLE_COLOR] = b.assistantBubbleColor
            p[Keys.AUTO_UPDATE_TIMING] = b.autoUpdateTiming
            p[Keys.SERVERS] = json.encodeToString(ListSerializer(ServerProfile.serializer()), b.servers)
            p[Keys.FAVORITES] = json.encodeToString(b.favorites)
            p[Keys.ARCHIVED] = json.encodeToString(b.archived)
            p[Keys.DRAFTS] = json.encodeToString(b.drafts)
        }
    }

    suspend fun setLastSessionId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.LAST_SESSION)
            else it[Keys.LAST_SESSION] = id
        }
    }
}
