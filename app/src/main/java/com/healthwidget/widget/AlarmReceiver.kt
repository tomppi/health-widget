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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Fire on both our alarm tick AND every time the user unlocks/returns
        // to the home screen — this fixes blank widgets after memory pressure
        if (intent.action != ACTION && intent.action != Intent.ACTION_USER_PRESENT) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, HealthAppWidget::class.java)
        )

        if (widgetIds.isEmpty()) {
            cancel(context)
            return
        }

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

        // Only re-schedule the timed alarm (not on USER_PRESENT)
        if (intent.action == ACTION) {
            schedule(context)
        }
    }

    companion object {
        const val ACTION = "com.healthwidget.WIDGET_ALARM"
        private const val INTERVAL_MS = 15 * 60 * 1000L

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
