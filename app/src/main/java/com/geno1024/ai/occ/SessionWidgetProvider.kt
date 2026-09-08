package com.geno1024.ai.occ

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SessionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val cached = readCached(context)
        val views = RemoteViews(context.packageName, R.layout.widget_session)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setImageViewResource(R.id.widget_icon, R.drawable.ic_launcher_foreground)
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, launch)
        views.setTextViewText(R.id.widget_session, cached.sessionTitle)
        views.setTextViewText(R.id.widget_questions, cached.questionsText)
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun readCached(context: Context): WidgetData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessionTitle = prefs.getString(KEY_SESSION, "") ?: ""
        val questions = prefs.getInt(KEY_QUESTIONS, 0)
        return WidgetData(sessionTitle, qText(context, questions))
    }

    private fun qText(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.widget_questions, count, count)

    private fun refresh(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.getAppWidgetIds(ComponentName(context, SessionWidgetProvider::class.java))
            .forEach { updateWidget(context, appWidgetManager, it) }
    }

    private data class WidgetData(val sessionTitle: String, val questionsText: String)

    companion object {
        const val PREFS_NAME = "poll_state"
        const val KEY_SESSION = "widget_session_title"
        const val KEY_QUESTIONS = "widget_questions"

        fun refreshAppWidgets(context: Context) {
            SessionWidgetProvider().refresh(context)
        }

        fun updateCached(context: Context, sessionTitle: String, questions: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSION, sessionTitle)
                .putInt(KEY_QUESTIONS, questions)
                .apply()
        }
    }
}
