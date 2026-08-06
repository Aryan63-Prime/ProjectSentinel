package com.sentinel.host

import android.app.Application
import com.sentinel.host.worker.SentinelWatchdogWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SentinelWatchdogWorker.schedule(this)
    }
}
