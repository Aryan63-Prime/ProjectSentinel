package com.sentinel.host.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getSmsLogs(limit: Int = 30): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            result.add("[NOTICE] READ_SMS runtime permission not granted on host.")
            result.add("[ACTION] Grant permission via Android Settings -> Apps -> Sentinel -> Permissions -> SMS.")
            return@withContext result
        }

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            if (cursor == null || !cursor.moveToFirst()) {
                result.add("[SMS_INBOX] Inbox is empty or no messages found.")
                return@withContext result
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

            do {
                val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "Unknown" else "Unknown"
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                val dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                val dateStr = if (dateMillis > 0) dateFormat.format(Date(dateMillis)) else "Unknown Date"
                val typeTag = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "INBOX"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "SENT"
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
                    else -> "SMS"
                }

                val sanitizedBody = body.replace("\n", " ").take(160)
                result.add("[$typeTag] $dateStr | $address: $sanitizedBody")
            } while (cursor.moveToNext() && result.size < limit)

        } catch (e: Exception) {
            result.add("[ERROR] Failed to query SMS ContentProvider: ${e.message}")
        } finally {
            cursor?.close()
        }

        result
    }
}
