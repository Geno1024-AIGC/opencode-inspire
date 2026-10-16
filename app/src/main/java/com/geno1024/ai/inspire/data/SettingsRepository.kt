package com.geno1024.ai.inspire.data

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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
    val mirror: String = "github",
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
    val dayStartOffsetMin: Int? = null,
)

@Serializable
data class DocPathsCache(
    val version: String? = null,
    val paths: List<String> = emptyList(),
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
        val MIRROR = stringPreferencesKey("mirror")
        val USER_BUBBLE_COLOR = longPreferencesKey("user_bubble_color")
        val ASSIST_BUBBLE_COLOR = longPreferencesKey("assistant_bubble_color")
        val AUTO_UPDATE_TIMING = booleanPreferencesKey("auto_update_timing")
        val TABLE_TIME_FORMAT = intPreferencesKey("table_time_format")
        val BACKGROUND_NOTIFY = booleanPreferencesKey("background_notify")
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
        val TOKEN_PROJECT_STATS = stringPreferencesKey("token_project_stats")
        val SESSION_MODEL_TOKENS = stringPreferencesKey("session_model_tokens")
        val TOKEN_SYNC = longPreferencesKey("token_sync")
        val TOKEN_SYNC_AT = longPreferencesKey("token_sync_at")
        val TOKEN_RAW_BUCKETS = stringPreferencesKey("token_raw_buckets")
        val TOKEN_RAW_SYNC = longPreferencesKey("token_raw_sync")
        val TOKEN_SESSION_SYNC = stringPreferencesKey("token_session_sync")
        val DAY_START_OFFSET_MIN = intPreferencesKey("day_start_offset_min")
        val DOWNLOADED_APK = stringPreferencesKey("downloaded_apk")
        val INSTALLED_VERSION = stringPreferencesKey("installed_version")
        val DRAFTS = stringPreferencesKey("drafts")
        val TOKEN_SIDS = stringPreferencesKey("token_sids")
        val DOC_PATHS = stringPreferencesKey("doc_paths")
        val TOKEN_STORE_VERSION = intPreferencesKey("token_store_version")
        const val TOKEN_STORE_PREFIX = "token_store:"
        const val TOKEN_STORE_VERSION_VALUE = 2
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
    val mirror: Flow<String> = context.dataStore.data.map { it[Keys.MIRROR] ?: "github" }
    val userBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.USER_BUBBLE_COLOR] ?: -1L }
    val assistantBubbleColor: Flow<Long> = context.dataStore.data.map { it[Keys.ASSIST_BUBBLE_COLOR] ?: -1L }
    val autoUpdateTiming: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE_TIMING] ?: false }
    val backgroundNotify: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKGROUND_NOTIFY] ?: false }
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
    val tokenRawBuckets: Flow<List<TokenRawBucket>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_RAW_BUCKETS]?.let { raw ->
            runCatching { json.decodeFromString<List<TokenRawBucket>>(raw) }.getOrNull()
        } ?: emptyList()
    }
    val tokenRawSync: Flow<Long> = context.dataStore.data.map { it[Keys.TOKEN_RAW_SYNC] ?: 0L }
    val tokenSessionSync: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_SESSION_SYNC]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrNull()
        } ?: emptyMap()
    }
    val dayStartOffset: Flow<Int?> = context.dataStore.data.map { it[Keys.DAY_START_OFFSET_MIN] }
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
    val tokenProjectStats: Flow<Map<String, TokenModelStats>> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOKEN_PROJECT_STATS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, TokenModelStats>>(raw) }.getOrNull()
        } ?: emptyMap()
    }
    val sessionModelTokens: Flow<Map<String, Map<String, TokenDay>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SESSION_MODEL_TOKENS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, Map<String, TokenDay>>>(raw) }.getOrNull()
        } ?: emptyMap()
    }

    private fun storeKey(sid: String) = stringPreferencesKey(Keys.TOKEN_STORE_PREFIX + sid)

    private fun decodeSids(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    val tokenSids: Flow<List<String>> = context.dataStore.data.map { prefs ->
        decodeSids(prefs[Keys.TOKEN_SIDS])
    }

    val tokenStores: Flow<Map<String, TokenStore>> = context.dataStore.data.map { prefs ->
        val sids = decodeSids(prefs[Keys.TOKEN_SIDS])
        sids.associateWith { sid ->
            prefs[storeKey(sid)]?.let { raw ->
                runCatching { json.decodeFromString<TokenStore>(raw) }.getOrNull()
            } ?: TokenStore()
        }
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

    suspend fun setMirror(mirror: String) {
        context.dataStore.edit { it[Keys.MIRROR] = mirror }
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

    suspend fun saveTokenProjectStats(stats: Map<String, TokenModelStats>) {
        context.dataStore.edit { it[Keys.TOKEN_PROJECT_STATS] = json.encodeToString(stats) }
    }

    suspend fun saveSessionModelTokens(stats: Map<String, Map<String, TokenDay>>) {
        context.dataStore.edit { it[Keys.SESSION_MODEL_TOKENS] = json.encodeToString(stats) }
    }

    suspend fun saveTokenRawBuckets(list: List<TokenRawBucket>) {
        context.dataStore.edit {
            it[Keys.TOKEN_RAW_BUCKETS] = json.encodeToString(ListSerializer(TokenRawBucket.serializer()), list)
        }
    }

    suspend fun saveTokenRawSync(ms: Long) {
        context.dataStore.edit { it[Keys.TOKEN_RAW_SYNC] = ms }
    }

    suspend fun saveTokenSessionSync(sync: Map<String, Long>) {
        context.dataStore.edit { it[Keys.TOKEN_SESSION_SYNC] = json.encodeToString(sync) }
    }

    suspend fun saveTokenStore(sid: String, store: TokenStore) {
        if (sid.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[storeKey(sid)] = json.encodeToString(TokenStore.serializer(), store)
            val sids = decodeSids(prefs[Keys.TOKEN_SIDS])
            if (sid !in sids) prefs[Keys.TOKEN_SIDS] = json.encodeToString(sids + sid)
        }
    }

    suspend fun removeTokenStore(sid: String) {
        if (sid.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs.remove(storeKey(sid))
            prefs[Keys.TOKEN_SIDS] = json.encodeToString(decodeSids(prefs[Keys.TOKEN_SIDS]) - sid)
        }
    }

    suspend fun mergeTokenStores(fromSid: String, intoSid: String) {
        if (fromSid.isBlank() || intoSid.isBlank() || fromSid == intoSid) return
        context.dataStore.edit { prefs ->
            val from = prefs[storeKey(fromSid)]?.let { raw ->
                runCatching { json.decodeFromString<TokenStore>(raw) }.getOrNull()
            }
            val into = prefs[storeKey(intoSid)]?.let { raw ->
                runCatching { json.decodeFromString<TokenStore>(raw) }.getOrNull()
            }
            if (from != null) {
                val merged = if (into == null) from else mergeTokenStoreDedup(into, from)
                prefs[storeKey(intoSid)] = json.encodeToString(TokenStore.serializer(), merged)
            }
            prefs.remove(storeKey(fromSid))
            prefs[Keys.TOKEN_SIDS] = json.encodeToString(
                (decodeSids(prefs[Keys.TOKEN_SIDS]) - fromSid + intoSid).distinct(),
            )
        }
    }

    suspend fun migrateLegacyTokenData(legacySid: String) {
        if (legacySid.isBlank()) return
        context.dataStore.edit { prefs ->
            if ((prefs[Keys.TOKEN_STORE_VERSION] ?: 0) >= Keys.TOKEN_STORE_VERSION_VALUE) return@edit

            fun <T> decodeLegacy(key: androidx.datastore.preferences.core.Preferences.Key<String>, block: (String) -> T?): T? =
                prefs[key]?.let { raw -> runCatching { block(raw) }.getOrNull() }

            val history = decodeLegacy(Keys.TOKEN_HISTORY) { json.decodeFromString<Map<String, TokenDay>>(it) }
            val elapsed = decodeLegacy(Keys.TOKEN_ELAPSED) { json.decodeFromString<Map<String, Long>>(it) }
            val month = decodeLegacy(Keys.TOKEN_MONTH) { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(it) }
            val week = decodeLegacy(Keys.TOKEN_WEEK) { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(it) }
            val dayHours = decodeLegacy(Keys.TOKEN_DAY_HOURS) { json.decodeFromString<Map<String, Map<Int, TokenDay>>>(it) }
            val modelStats = decodeLegacy(Keys.TOKEN_MODEL_STATS) { json.decodeFromString<Map<String, TokenModelStats>>(it) }
            val projectStats = decodeLegacy(Keys.TOKEN_PROJECT_STATS) { json.decodeFromString<Map<String, TokenModelStats>>(it) }
            val sessionModelTokens = decodeLegacy(Keys.SESSION_MODEL_TOKENS) { json.decodeFromString<Map<String, Map<String, TokenDay>>>(it) }
            val rawBuckets = decodeLegacy(Keys.TOKEN_RAW_BUCKETS) { json.decodeFromString<List<TokenRawBucket>>(it) }
            val sessionSync = decodeLegacy(Keys.TOKEN_SESSION_SYNC) { json.decodeFromString<Map<String, Long>>(it) }
            val sync = prefs[Keys.TOKEN_SYNC]
            val syncedAt = prefs[Keys.TOKEN_SYNC_AT]
            val rawSync = prefs[Keys.TOKEN_RAW_SYNC]

            val hasLegacy = history != null || elapsed != null || month != null || week != null ||
                dayHours != null || modelStats != null || projectStats != null || sessionModelTokens != null ||
                rawBuckets != null || sessionSync != null || sync != null || syncedAt != null || rawSync != null
            if (hasLegacy) {
                val legacy = TokenStore(
                    history = history ?: emptyMap(),
                    elapsed = elapsed ?: emptyMap(),
                    month = month ?: emptyMap(),
                    week = week ?: emptyMap(),
                    dayHours = dayHours ?: emptyMap(),
                    modelStats = modelStats ?: emptyMap(),
                    projectStats = projectStats ?: emptyMap(),
                    sessionModelTokens = sessionModelTokens ?: emptyMap(),
                    sync = sync ?: 0L,
                    syncedAt = syncedAt ?: 0L,
                    rawBuckets = rawBuckets ?: emptyList(),
                    rawSync = rawSync ?: 0L,
                    sessionSync = sessionSync ?: emptyMap(),
                )
                val existing = prefs[storeKey(legacySid)]?.let { raw ->
                    runCatching { json.decodeFromString<TokenStore>(raw) }.getOrNull()
                }
                val merged = if (existing == null) legacy else mergeTokenStoreDedup(existing, legacy)
                prefs[storeKey(legacySid)] = json.encodeToString(TokenStore.serializer(), merged)
                prefs[Keys.TOKEN_SIDS] = json.encodeToString((decodeSids(prefs[Keys.TOKEN_SIDS]) + legacySid).distinct())
                prefs.remove(Keys.TOKEN_HISTORY)
                prefs.remove(Keys.TOKEN_ELAPSED)
                prefs.remove(Keys.TOKEN_MONTH)
                prefs.remove(Keys.TOKEN_WEEK)
                prefs.remove(Keys.TOKEN_DAY_HOURS)
                prefs.remove(Keys.TOKEN_MODEL_STATS)
                prefs.remove(Keys.TOKEN_PROJECT_STATS)
                prefs.remove(Keys.SESSION_MODEL_TOKENS)
                prefs.remove(Keys.TOKEN_RAW_BUCKETS)
                prefs.remove(Keys.TOKEN_SESSION_SYNC)
                prefs.remove(Keys.TOKEN_SYNC)
                prefs.remove(Keys.TOKEN_SYNC_AT)
                prefs.remove(Keys.TOKEN_RAW_SYNC)
            }
            prefs[Keys.TOKEN_STORE_VERSION] = Keys.TOKEN_STORE_VERSION_VALUE
        }
    }

    suspend fun setDayStartOffset(offsetMinutes: Int?) {
        context.dataStore.edit { p ->
            if (offsetMinutes == null) p.remove(Keys.DAY_START_OFFSET_MIN)
            else p[Keys.DAY_START_OFFSET_MIN] = offsetMinutes
        }
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

    suspend fun setBackgroundNotify(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_NOTIFY] = enabled }
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

    suspend fun saveServers(profiles: List<ServerProfile>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVERS] = json.encodeToString(ListSerializer(ServerProfile.serializer()), profiles)
        }
    }

    suspend fun saveDocPaths(url: String, version: String?, paths: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DOC_PATHS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, DocPathsCache>>(raw) }.getOrNull()
            } ?: emptyMap()
            prefs[Keys.DOC_PATHS] = json.encodeToString(
                MapSerializer(String.serializer(), DocPathsCache.serializer()),
                current + (url to DocPathsCache(version = version, paths = paths)),
            )
        }
    }

    suspend fun getDocPaths(url: String): DocPathsCache? {
        val raw = context.dataStore.data.first()[Keys.DOC_PATHS] ?: return null
        val map = runCatching { json.decodeFromString<Map<String, DocPathsCache>>(raw) }.getOrNull() ?: return null
        return map[url]
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
            mirror = p[Keys.MIRROR] ?: "github",
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
            dayStartOffsetMin = p[Keys.DAY_START_OFFSET_MIN],
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
            if (b.dayStartOffsetMin == null) p.remove(Keys.DAY_START_OFFSET_MIN)
            else p[Keys.DAY_START_OFFSET_MIN] = b.dayStartOffsetMin
        }
    }

    suspend fun setLastSessionId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.LAST_SESSION)
            else it[Keys.LAST_SESSION] = id
        }
    }
}
