package com.geno1024.ai.occ

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import com.geno1024.ai.occ.data.AgentClientRegistry
import com.geno1024.ai.occ.data.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionPollService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(POLL_CHANNEL, getString(R.string.notify_poll_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        runCatching {
            startForeground(POLL_NOTIF_ID, ongoingNotification(getString(R.string.notify_poll_starting)))
        }
        job = scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollLoop() {
        while (scope.isActive) {
            pollOnce()
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollOnce() {
        val settings = SettingsRepository(applicationContext)
        val url = withContext(Dispatchers.IO) { settings.serverUrl.first() } ?: run {
            stopSelf()
            return
        }
        val username = withContext(Dispatchers.IO) { settings.authUsername.first() }
        val password = withContext(Dispatchers.IO) { settings.authPassword.first() }
        val client = AgentClientRegistry.create(url, username, password)
        var newMessageTime = 0L
        var sessionTitle = ""
        var questions = 0
        var permissions = 0
        runCatching {
            val active = withContext(Dispatchers.IO) { client.sessions() }.firstOrNull()
            if (active != null) {
                sessionTitle = active.title.ifBlank { active.id }
                val page = withContext(Dispatchers.IO) { client.sessionMessagesPage(active.id, 1, null) }
                newMessageTime = page.first.firstOrNull()?.first?.time?.created?.let { serverToMillis(it) } ?: 0L
            }
            questions = withContext(Dispatchers.IO) { client.pendingQuestions(active?.directory) }.size
            val permDir = active?.directory
            permissions = if (permDir.isNullOrBlank()) {
                withContext(Dispatchers.IO) { client.pendingPermissions(null) }.size
            } else {
                runCatching {
                    withContext(Dispatchers.IO) { client.pendingPermissions(permDir) }.size
                }.getOrElse {
                    withContext(Dispatchers.IO) { client.pendingPermissions(null) }.size
                }
            }
            val lastSession = prefs.getString(KEY_LAST_SESSION, null)
            val lastTime = prefs.getLong(KEY_LAST_TIME, 0L)
            val lastQuestions = prefs.getInt(KEY_LAST_QUESTIONS, 0)
            val lastPermissions = prefs.getInt(KEY_LAST_PERMISSIONS, 0)

            if (newMessageTime > 0L && (lastSession != active?.id || newMessageTime != lastTime)) {
                postNewMessages(sessionTitle)
            }
            if (questions > 0 && (lastQuestions < questions)) {
                postQuestions(questions, sessionTitle)
            }
            if (permissions > 0 && (lastPermissions < permissions)) {
                postPermissionRequests(permissions, sessionTitle)
            }

            prefs.edit()
                .putString(KEY_LAST_SESSION, active?.id)
                .putLong(KEY_LAST_TIME, newMessageTime)
                .putInt(KEY_LAST_QUESTIONS, questions)
                .putInt(KEY_LAST_PERMISSIONS, permissions)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
        SessionWidgetProvider.updateCached(applicationContext, sessionTitle, questions)
        SessionWidgetProvider.refreshAppWidgets(applicationContext)
        runCatching {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(prefs.getLong(KEY_LAST_CHECK, System.currentTimeMillis())))
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(POLL_NOTIF_ID, ongoingNotification(getString(R.string.notify_poll_running, time)))
        }
    }

    private fun ongoingNotification(text: String): Notification =
        android.app.Notification.Builder(this, POLL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notify_poll_title))
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun postNewMessages(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = android.app.Notification.Builder(this, POLL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notify_new_messages_title, title.ifBlank { "OpenCode" }))
            .setContentText(getString(R.string.notify_new_messages))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NEW_MSG_NOTIF_ID, notification) }
    }

    private fun postQuestions(count: Int, title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = android.app.Notification.Builder(this, POLL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(getString(R.string.notify_questions_title, title.ifBlank { "OpenCode" }))
            .setContentText(resources.getQuantityString(R.plurals.notify_questions, count, count))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(QUESTIONS_NOTIF_ID, notification) }
    }

    private fun postPermissionRequests(count: Int, title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = android.app.Notification.Builder(this, POLL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.notify_permissions_title, title.ifBlank { "OpenCode" }))
            .setContentText(resources.getQuantityString(R.plurals.notify_permissions, count, count))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(PERMS_NOTIF_ID, notification) }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serverToMillis(v: Long): Long = if (v < 1_000_000_000_000L) v * 1000L else v

    companion object {
        private const val POLL_CHANNEL = "poll_status"
        private const val POLL_NOTIF_ID = 2000
        private const val NEW_MSG_NOTIF_ID = 2001
        private const val QUESTIONS_NOTIF_ID = 2002
        private const val PERMS_NOTIF_ID = 2003
        private const val PREFS_NAME = "poll_state"
        private const val KEY_LAST_SESSION = "last_session_id"
        private const val KEY_LAST_TIME = "last_msg_time"
        private const val KEY_LAST_QUESTIONS = "last_questions"
        private const val KEY_LAST_PERMISSIONS = "last_permissions"
        private const val KEY_LAST_CHECK = "last_check"
        private const val POLL_INTERVAL_MS = 60_000L
    }
}