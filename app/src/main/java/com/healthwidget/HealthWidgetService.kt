package com.healthwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.healthwidget.widget.AlarmReceiver
import com.healthwidget.widget.HealthWidgetUpdateWorker

class HealthWidgetService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Keep both schedulers running from within the service process
        HealthWidgetUpdateWorker.schedule(this)
        AlarmReceiver.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY tells Android to restart the service if it gets killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Widget",
            NotificationManager.IMPORTANCE_MIN  // silent, no sound, no pop-up
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
        private const val CHANNEL_ID     = "health_widget_service"
        private const val NOTIFICATION_ID = 1
    }
}
