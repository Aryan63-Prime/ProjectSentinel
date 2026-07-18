package com.sentinel.host.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.sentinel.host.domain.network.NetworkObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps ConnectivityManager to observe network availability.
 * Emits true/false via [isAvailable] StateFlow.
 *
 * Lifecycle: call [start] once, [stop] to unregister.
 * Thread-safe: ConnectivityManager callbacks and StateFlow are both thread-safe.
 */
class AndroidNetworkObserver(context: Context) : NetworkObserver {

    companion object {
        private const val TAG = "Sentinel:Network"
    }

    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isAvailable = MutableStateFlow(checkCurrentNetwork())
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available")
            _isAvailable.value = true
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost")
            _isAvailable.value = false
        }

        override fun onUnavailable() {
            Log.i(TAG, "Network unavailable")
            _isAvailable.value = false
        }
    }

    override fun start() {
        if (registered) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        Log.i(TAG, "Network observer started (available=${_isAvailable.value})")
    }

    override fun stop() {
        if (!registered) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        registered = false
        Log.i(TAG, "Network observer stopped")
    }

    private fun checkCurrentNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
