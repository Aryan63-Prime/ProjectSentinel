package com.sentinel.admin.domain.model

/**
 * An individual contact entry extracted directly from a host device's address book.
 *
 * @property name Display name of the contact.
 * @property phone Contact phone number.
 * @property type Phone type label (e.g. "Mobile", "Home", "Work", "Other").
 * @property isEmergency Whether this contact is identified as an emergency / ICE contact.
 */
data class DeviceContact(
    val name: String,
    val phone: String,
    val type: String = "Mobile",
    val isEmergency: Boolean = false
)

/**
 * The synchronized address book for a monitored device.
 *
 * @property deviceId ID of the monitored device.
 * @property total Total number of contacts extracted from the host device.
 * @property contacts List of all contacts extracted from the host device.
 * @property emergencyContact Primary ICE / emergency contact if detected.
 * @property lastSynced Formatted timestamp or status when contacts were retrieved.
 */
data class DeviceContactBook(
    val deviceId: String,
    val total: Int = 0,
    val contacts: List<DeviceContact> = emptyList(),
    val emergencyContact: DeviceContact? = null,
    val lastSynced: String? = null
)
