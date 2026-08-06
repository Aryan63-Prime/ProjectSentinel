package com.sentinel.host

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SentinelWatchdogWorker.schedule(this)
    }
}
