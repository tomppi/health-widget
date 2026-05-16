package com.healthwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Periodic background job that refreshes all active widget instances.
 *
 * Android enforces a minimum interval of 15 minutes for [PeriodicWorkRequest].
 * The widget's own updatePeriodMillis (in health_widget_info.xml) is set to
 * 0 to disable the system-driven updates and let WorkManager be the sole
 * scheduler — this avoids duplicate refreshes and saves battery.
 */
class HealthWidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, HealthAppWidget::class.java)
        )

        if (widgetIds.isEmpty()) return Result.success() // nothing pinned

        widgetIds.forEach { id ->
            HealthAppWidget.updateWidget(applicationContext, appWidgetManager, id)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "health_widget_periodic_update"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthWidgetUpdateWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
