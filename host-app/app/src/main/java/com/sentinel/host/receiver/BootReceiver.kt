package com.sentinel.host.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.host.service.SentinelForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("Sentinel", "Broadcast event: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.i("Sentinel", "Starting service from boot/update")
                startSentinelService(context)
            }

            else -> {
                Log.i("Sentinel", "Starting service from other broadcast: $action")
                startSentinelService(context)
            }
        }
    }

    private fun startSentinelService(context: Context) {
        try {
            Log.i("Sentinel", "Attempting to start SentinelForegroundService...")
            SentinelForegroundService.Start(context)
            Log.i("Sentinel", "Service start command sent successfully")
        } catch (e: Exception) {
            // This can happen on Android 12+ due to Background Service Start Restrictions
            // if the broadcast is not one of the exempted ones.
            Log.e("Sentinel", "Failed to start service from background: ${e.message}", e)
        }
    }
}
