package com.healthwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.RemoteViews
import com.healthwidget.HealthConnectManager
import com.healthwidget.HealthData
import com.healthwidget.HeartRateChartView
import com.healthwidget.SleepChartView
import com.healthwidget.SleepStageData
import com.healthwidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthAppWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> showRefreshing(context, appWidgetManager, id) }
        HealthWidgetUpdateWorker.runNow(context)
        HealthWidgetUpdateWorker.schedule(context)
        AlarmReceiver.schedule(context)
    }

    override fun onEnabled(context: Context) {
        HealthWidgetUpdateWorker.schedule(context)
        AlarmReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        HealthWidgetUpdateWorker.cancel(context)
        AlarmReceiver.cancel(context)
    }

    companion object {

        private const val CHART_W = 360
        private const val CHART_H = 100

        private fun showRefreshing(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            RemoteViews(context.packageName, R.layout.widget_layout).apply {
                setTextViewText(R.id.tv_updated, "Refreshing…")
                appWidgetManager.updateAppWidget(appWidgetId, this)
            }
        }

        private fun heartBitmap(heartRates: List<Int>): Bitmap {
            val bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.parseColor("#0D1525"))
            HeartRateChartView.drawChartOnCanvas(canvas, heartRates, CHART_W.toFloat(), CHART_H.toFloat())
            return bmp
        }

        private fun sleepBitmap(stages: List<SleepStageData>): Bitmap {
            val bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.parseColor("#0D1525"))
            SleepChartView.drawChartOnCanvas(canvas, stages, CHART_W.toFloat(), CHART_H.toFloat())
            return bmp
        }

        private fun applyDataToViews(
            context: Context,
            views: RemoteViews,
            data: HealthData
        ) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            views.setImageViewBitmap(R.id.iv_heart_chart, heartBitmap(data.heartRates))
            views.setImageViewBitmap(R.id.iv_sleep_chart, sleepBitmap(data.sleepStages))
            views.setTextViewText(R.id.tv_updated, "Updated ${fmt.format(Date())}")
        }

        /**
         * Called from MainActivity — uses data already fetched by the app
         * so the widget shows exactly the same readings the app just displayed.
         * No second Health Connect read.
         */
        fun updateWidgetWithData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: HealthData
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            applyDataToViews(context, views, data)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Called from WorkManager / AlarmReceiver / Service — reads fresh data
         * from Health Connect because there is no pre-fetched data available.
         */
        suspend fun updateWidgetData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val manager = HealthConnectManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            if (!manager.isAvailable() || !manager.hasAllPermissions()) {
                views.setImageViewBitmap(R.id.iv_heart_chart, heartBitmap(emptyList()))
                views.setImageViewBitmap(R.id.iv_sleep_chart, sleepBitmap(emptyList()))
                views.setTextViewText(R.id.tv_updated, "Open app to grant permissions")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            runCatching {
                val data = manager.getLatestHealthData()
                applyDataToViews(context, views, data)
            }.onFailure {
                views.setImageViewBitmap(R.id.iv_heart_chart, heartBitmap(emptyList()))
                views.setImageViewBitmap(R.id.iv_sleep_chart, sleepBitmap(emptyList()))
                views.setTextViewText(R.id.tv_updated, "Sync error")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
