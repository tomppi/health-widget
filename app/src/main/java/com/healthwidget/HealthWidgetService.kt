package com.healthwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import com.healthwidget.widget.AlarmReceiver
import com.healthwidget.widget.HealthAppWidget
import com.healthwidget.widget.HealthWidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HealthWidgetService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        HealthWidgetUpdateWorker.schedule(this)
        AlarmReceiver.schedule(this)
        startWatchingHealthConnect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        watchJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Polls Health Connect for changes every 60 seconds.
     * When new heart rate or sleep data arrives, immediately updates the widget.
     *
     * Health Connect doesn't support push notifications, so we use a short
     * polling interval from within the foreground service instead.
     * 60 seconds is a good balance — responsive but not battery intensive.
     */
    private fun startWatchingHealthConnect() {
        watchJob = scope.launch {
            val client = HealthConnectClient.getOrCreate(this@HealthWidgetService)

            // Get a token representing the current state of Health Connect data
            var token = try {
                client.getChangesToken(
                    ChangesTokenRequest(
                        recordTypes = setOf(
                            HeartRateRecord::class,
                            SleepSessionRecord::class
                        )
                    )
                )
            } catch (e: Exception) {
                null
            }

            while (isActive) {
                delay(60_000L) // check every 60 seconds

                if (token == null) {
                    // Try to get a token if we didn't have one
                    token = try {
                        client.getChangesToken(
                            ChangesTokenRequest(
                                recordTypes = setOf(
                                    HeartRateRecord::class,
                                    SleepSessionRecord::class
                                )
                            )
                        )
                    } catch (e: Exception) {
                        null
                    }
                    continue
                }

                try {
                    val response = client.getChanges(token)

                    if (response.changes.isNotEmpty()) {
                        // New data written to Health Connect — update the widget
                        updateAllWidgets()
                    }

                    // Always advance the token so we don't re-process old changes
                    token = response.nextChangesToken

                } catch (e: Exception) {
                    // Token may have expired — get a fresh one next iteration
                    token = null
                }
            }
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(this, HealthAppWidget::class.java)
        )
        scope.launch {
            ids.forEach { id ->
                HealthAppWidget.updateWidgetData(this@HealthWidgetService, appWidgetManager, id)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Widget",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the health widget updated in the background"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Health Widget running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID      = "health_widget_service"
        private const val NOTIFICATION_ID = 1
    }
}
