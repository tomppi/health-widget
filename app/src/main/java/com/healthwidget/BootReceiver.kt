package com.healthwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthwidget.widget.AlarmReceiver
import com.healthwidget.widget.HealthWidgetUpdateWorker

/**
 * Restarts both WorkManager and AlarmManager after device reboot.
 * Both are cancelled on reboot so must be explicitly rescheduled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HealthWidgetUpdateWorker.schedule(context)
            AlarmReceiver.schedule(context)
        }
    }
}
