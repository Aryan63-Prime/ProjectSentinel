package com.sentinel.admin.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sentinel.admin.domain.model.DeviceContact
import com.sentinel.admin.domain.model.DeviceContactBook
import com.sentinel.admin.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ContactRepository] backed by SharedPreferences with in-memory caching.
 * Persists authentic contacts extracted from monitored devices. No mock/placeholder data.
 */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    context: Context
) : ContactRepository {

    companion object {
        private const val PREFS_NAME = "sentinel_device_contacts"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val contactFlows = ConcurrentHashMap<String, MutableStateFlow<DeviceContactBook?>>()

    override fun getContactBook(deviceId: String): Flow<DeviceContactBook?> {
        val flow = contactFlows.computeIfAbsent(deviceId) {
            MutableStateFlow(loadFromPrefs(deviceId))
        }
        return flow.asStateFlow()
    }

    override suspend fun saveContactBook(
        deviceId: String,
        contactBook: DeviceContactBook
    ): Result<Unit> {
        return try {
            val contactsArray = JSONArray()
            contactBook.contacts.forEach { contact ->
                val cJson = JSONObject().apply {
                    put("name", contact.name)
                    put("phone", contact.phone)
                    put("type", contact.type)
                    put("isEmergency", contact.isEmergency)
                }
                contactsArray.put(cJson)
            }

            val emergencyJson = contactBook.emergencyContact?.let {
                JSONObject().apply {
                    put("name", it.name)
                    put("phone", it.phone)
                    put("type", it.type)
                    put("isEmergency", it.isEmergency)
                }
            }

            val rootJson = JSONObject().apply {
                put("deviceId", contactBook.deviceId)
                put("total", contactBook.total)
                put("contacts", contactsArray)
                if (emergencyJson != null) put("emergencyContact", emergencyJson)
                put("lastSynced", contactBook.lastSynced ?: "")
            }

            prefs.edit().putString("contact_book_$deviceId", rootJson.toString()).apply()

            val flow = contactFlows.computeIfAbsent(deviceId) {
                MutableStateFlow(contactBook)
            }
            flow.value = contactBook

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadFromPrefs(deviceId: String): DeviceContactBook? {
        val raw = prefs.getString("contact_book_$deviceId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val total = json.optInt("total", 0)
            val lastSynced = json.optString("lastSynced").takeIf { it.isNotBlank() }

            val contactsList = mutableListOf<DeviceContact>()
            val contactsArr = json.optJSONArray("contacts")
            if (contactsArr != null) {
                for (i in 0 until contactsArr.length()) {
                    val cObj = contactsArr.optJSONObject(i)
                    if (cObj != null) {
                        contactsList.add(
                            DeviceContact(
                                name = cObj.optString("name"),
                                phone = cObj.optString("phone"),
                                type = cObj.optString("type", "Mobile"),
                                isEmergency = cObj.optBoolean("isEmergency", false)
                            )
                        )
                    }
                }
            }

            var emergencyContact: DeviceContact? = null
            val emObj = json.optJSONObject("emergencyContact")
            if (emObj != null) {
                emergencyContact = DeviceContact(
                    name = emObj.optString("name"),
                    phone = emObj.optString("phone"),
                    type = emObj.optString("type", "Mobile"),
                    isEmergency = true
                )
            }

            DeviceContactBook(
                deviceId = deviceId,
                total = if (total > 0) total else contactsList.size,
                contacts = contactsList,
                emergencyContact = emergencyContact,
                lastSynced = lastSynced
            )
        } catch (_: Exception) {
            null
        }
    }
}
