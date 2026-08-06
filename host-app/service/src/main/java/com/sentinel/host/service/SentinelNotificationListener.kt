package com.sentinel.host.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sentinel.host.data.device.SentinelLogBuffer

class SentinelNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "Sentinel:NotifListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "SentinelNotificationListener connected successfully")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: "unknown"
            if (packageName == applicationContext.packageName) return

            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            val fullText = if (subText.isNotBlank()) "$text ($subText)" else text

            if (title.isNotBlank() || fullText.isNotBlank()) {
                SentinelLogBuffer.instance.addNotification(packageName, title, fullText)
                Log.i(TAG, "Captured notification from $packageName: $title — $fullText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification: ${e.message}", e)
        }
    }
}
