package com.healthwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.healthwidget.widget.AlarmReceiver
import com.healthwidget.widget.HealthWidgetUpdateWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HealthWidgetUpdateWorker.schedule(context)
            AlarmReceiver.schedule(context)
            // Restart the foreground service after reboot
            val serviceIntent = Intent(context, HealthWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
