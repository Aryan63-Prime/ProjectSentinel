package com.sentinel.host.data.device

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:RemoteFile"
    }

    fun listDirectory(requestedPath: String, sequence: Long): String {
        val targetPath = if (requestedPath.isBlank() || requestedPath == "/") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            requestedPath
        }

        Log.i(TAG, "Listing directory for path: $targetPath")
        val dir = File(targetPath)
        val itemsArray = JSONArray()

        if (dir.exists() && dir.isDirectory) {
            val fileList = dir.listFiles()
            if (fileList != null) {
                // Sort directories first, then files alphabetically
                val sortedFiles = fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                for (file in sortedFiles) {
                    val item = JSONObject().apply {
                        put("name", file.name)
                        put("is_dir", file.isDirectory)
                        put("size", if (file.isDirectory) 0L else file.length())
                        put("last_modified", file.lastModified())
                    }
                    itemsArray.put(item)
                }
            } else {
                Log.w(TAG, "listFiles() returned null for path: $targetPath")
            }
        } else {
            Log.w(TAG, "Directory does not exist or is not a directory: $targetPath")
        }

        val responseJson = JSONObject().apply {
            put("type", "FILES_LIST_RES")
            put("version", 1)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("sequence", sequence)
            put("data", JSONObject().apply {
                put("path", targetPath)
                put("items", itemsArray)
            })
        }

        return responseJson.toString()
    }
}
