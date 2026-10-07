package com.sentinel.admin.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.admin.domain.repository.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EmergencyDismissReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: EmergencyNotificationManager

    @Inject
    lateinit var deviceRepository: DeviceRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            EmergencyNotificationManager.ACTION_DISMISS_EMERGENCY -> {
                Log.i("Sentinel:DismissReceiver", "Received emergency dismissal request")
                notificationManager.silenceAlarm()
                deviceRepository.dismissEmergencyAlert()
            }
            "com.sentinel.admin.ACTION_TEST_EMERGENCY" -> {
                Log.w("Sentinel:DismissReceiver", "Received test emergency request via broadcast")
                val callsign = intent.getStringExtra("callsign") ?: "HOST-02"
                val model = intent.getStringExtra("model") ?: "vivo I2401"
                val impact = intent.getFloatExtra("impact", 4.8f).toDouble()
                val alert = com.sentinel.admin.domain.model.EmergencyAlert(
                    deviceId = "HOST-001",
                    callsign = callsign,
                    model = model,
                    triggerReason = "FALL_DETECTED",
                    impactGForce = impact,
                    battery = 89
                )
                deviceRepository.triggerTestEmergencyAlert(alert)
            }
        }
    }
}
