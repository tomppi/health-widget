package com.healthwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthwidget.widget.HealthWidgetUpdateWorker

/**
 * Reschedules the periodic WorkManager job after a device reboot.
 * WorkManager persists jobs across reboots on most devices, but this
 * receiver acts as a safety net for cases where it doesn't.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HealthWidgetUpdateWorker.schedule(context)
        }
    }
}
