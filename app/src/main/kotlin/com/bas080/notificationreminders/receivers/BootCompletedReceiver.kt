package com.bas080.notificationreminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.log(context, "BootCompletedReceiver", "Received action: $action")
        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            ReminderNotificationListenerService.startService(context)
        }
    }
}
