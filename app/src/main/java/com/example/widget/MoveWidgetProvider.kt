package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.MoveRoomDatabase
import com.example.domain.DateStreakUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MoveWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsAsync(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, MoveWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetsAsync(appContext, manager, ids)
            }
        }

        private suspend fun updateWidgetsAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val dao = MoveRoomDatabase.getInstance(context).moveDao()
            val profile = dao.getUserProfileOnce()
            val workouts = dao.getAllWorkoutsOnce()
            val streak = DateStreakUtils.calculateStreak(
                workouts = workouts,
                storedLongestStreak = profile?.longestStreak ?: 0
            )
            val goal = DateStreakUtils.calculateDailyGoalSummary(
                workouts = workouts,
                goalMinutes = profile?.dailyGoalMinutes ?: 0
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeText = if (goal.completedSecondsToday > 0) {
                "วันนี้ ${DateStreakUtils.formatMinutesThai(goal.completedSecondsToday)}"
            } else {
                "วันนี้ 0 นาที • พร้อมเริ่ม!"
            }
            val goalText = if (goal.isFreeMode) {
                "${goal.caloriesToday} kcal • เวลาอิสระ"
            } else {
                "${goal.caloriesToday} kcal • เป้า ${goal.progressPercent}%"
            }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_move_summary).apply {
                    setTextViewText(R.id.widget_streak_pill, "${streak.currentStreak} วันติด")
                    setTextViewText(R.id.widget_today_minutes, timeText)
                    setTextViewText(R.id.widget_today_stats, goalText)
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
