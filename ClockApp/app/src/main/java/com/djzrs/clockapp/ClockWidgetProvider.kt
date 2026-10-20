package com.djzrs.clockapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 桌面时钟小组件。
 * 使用 AlarmManager 每分钟刷新一次时间文本。
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ClockWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }
    }

    override fun onEnabled(context: Context) {
        startAlarm(context)
    }

    override fun onDisabled(context: Context) {
        stopAlarm(context)
    }

    companion object {
        const val ACTION_UPDATE = "com.djzrs.clockapp.WIDGET_UPDATE"

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            val c = Calendar.getInstance()
            val time = String.format(
                "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE)
            )
            val date = String.format(
                "%04d-%02d-%02d %s",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                widgetWeekCn(c.get(Calendar.DAY_OF_WEEK))
            )
            views.setTextViewText(R.id.widgetTime, time)
            views.setTextViewText(R.id.widgetDate, date)
            manager.updateAppWidget(appWidgetId, views)
        }

        fun startAlarm(context: Context) {
            val intent = Intent(context, ClockWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                60_000L,
                pendingIntent
            )
        }

        fun stopAlarm(context: Context) {
            val intent = Intent(context, ClockWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }

        private fun widgetWeekCn(day: Int): String = when (day) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }
}