package com.healthwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.healthwidget.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives AlarmManager ticks every 15 minutes and updates all widget instances.
 * AlarmManager is much harder for Android to kill than WorkManager, making it
 * a reliable fallback when background processes are aggressively killed.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, HealthAppWidget::class.java)
        )

        if (widgetIds.isEmpty()) {
            // No widgets pinned — cancel the alarm to save battery
            cancel(context)
            return
        }

        // Use goAsync() to give the BroadcastReceiver extra time to complete
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                widgetIds.forEach { id ->
                    HealthAppWidget.updateWidgetData(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
        }

        // Re-schedule the next alarm
        schedule(context)
    }

    companion object {
        const val ACTION = "com.healthwidget.WIDGET_ALARM"
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION).apply { setPackage(context.packageName) }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = getPendingIntent(context)
            val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS

            // Use setExactAndAllowWhileIdle so the alarm fires even in Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(getPendingIntent(context))
        }
    }
}
