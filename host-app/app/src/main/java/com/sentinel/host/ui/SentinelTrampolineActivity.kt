package com.sentinel.host.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * 1ms Transparent Trampoline Activity.
 *
 * Briefly elevates the Host process state to TOP so that Android 14+ (API 34/35/36)
 * AudioFlinger and privacy guards grant unmuted hardware microphone access
 * when audio streaming starts from the background or while the screen is locked.
 *
 * Completely invisible to the user and finishes immediately with zero animations.
 */
class SentinelTrampolineActivity : Activity() {

    companion object {
        private const val TAG = "Sentinel:Trampoline"
        const val EXTRA_START_AUDIO = "EXTRA_START_AUDIO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window completely invisible, non-touchable and non-focusable
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTranslucent(true)
        }

        Log.d(TAG, "Trampoline activity engaged — process elevated to TOP for unmuted HAL access")

        // Dismiss immediately with zero animation
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
