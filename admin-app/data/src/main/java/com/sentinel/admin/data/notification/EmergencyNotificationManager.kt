package com.sentinel.admin.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sentinel.admin.domain.model.EmergencyAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:EmergencyNotif"
        const val CHANNEL_ID = "sentinel_emergency_alerts"
        const val NOTIFICATION_ID = 9999
        const val ACTION_DISMISS_EMERGENCY = "com.sentinel.admin.ACTION_DISMISS_EMERGENCY"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sentinel Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority alarms and notifications for Fall Detection and Distress SOS"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 1000)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.i(TAG, "Emergency notification channel registered")
            }
        }
    }

    fun triggerEmergencyAlert(alert: EmergencyAlert) {
        Log.e(TAG, "Triggering emergency alert for ${alert.callsign} (${alert.model}), impact: ${alert.impactGForce}g")

        // 1. Play high-decibel alarm audio
        playAlarmSound()

        // 2. Vibrate phone
        startVibration()

        // 3. Post Heads-up Notification
        postHeadsUpNotification(alert)
    }

    private fun postHeadsUpNotification(alert: EmergencyAlert) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("selectedDeviceId", alert.deviceId)
                putExtra("isEmergencyAlert", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, EmergencyDismissReceiver::class.java).apply {
                action = ACTION_DISMISS_EMERGENCY
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID + 1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val gForceFormatted = String.format(java.util.Locale.US, "%.1f", alert.impactGForce)
            val title = "🚨 FALL DETECTED: ${alert.callsign} (${alert.model})"
            val text = "Impact of ${gForceFormatted}g detected! Tap to respond & view live feed."

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "$text\n• Callsign: ${alert.callsign}\n• Model: ${alert.model}\n• Battery: ${alert.battery ?: 0}%\n• Reason: ${alert.triggerReason}"
                ))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Silence Alarm",
                    dismissPendingIntent
                )

            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.i(TAG, "Heads-up emergency notification posted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post emergency notification: ${e.message}", e)
        }
    }

    private fun playAlarmSound() {
        try {
            silenceAlarmSound()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri != null) {
                try {
                    val rt = RingtoneManager.getRingtone(context, alarmUri)
                    if (rt != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            rt.isLooping = true
                        }
                        rt.play()
                        ringtone = rt
                        Log.i(TAG, "Emergency siren playback started via RingtoneManager")
                        return
                    }
                } catch (rtEx: Exception) {
                    Log.w(TAG, "Ringtone playback fallback to MediaPlayer: ${rtEx.message}")
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.i(TAG, "Alarm siren playback started via MediaPlayer")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play alarm siren audio: ${e.message}")
        }
    }

    private fun startVibration() {
        try {
            val timings = longArrayOf(0, 500, 200, 500, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    fun silenceAlarm() {
        silenceAlarmSound()
        try {
            vibrator?.cancel()
            notificationManager.cancel(NOTIFICATION_ID)
            Log.i(TAG, "Emergency alert silenced and notification cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling notification: ${e.message}")
        }
    }

    private fun silenceAlarmSound() {
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio: ${e.message}")
        }
    }
}
