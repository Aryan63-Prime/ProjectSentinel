package com.sentinel.host.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SentinelDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "Sentinel:DeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Sentinel Device Admin enabled successfully")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Sentinel Device Management will disable enterprise fleet tracking and security policies."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Sentinel Device Admin disabled")
    }
}
