package com.sentinel.admin.data.repository

import com.sentinel.admin.domain.model.DeviceContact
import com.sentinel.admin.domain.model.DeviceContactBook
import com.sentinel.admin.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ContactRepository] interface and flow contracts.
 */
class ContactRepositoryTest {

    private class TestContactRepository : ContactRepository {
        private val storage = mutableMapOf<String, MutableStateFlow<DeviceContactBook?>>()

        override fun getContactBook(deviceId: String): Flow<DeviceContactBook?> {
            val flow = storage.computeIfAbsent(deviceId) {
                MutableStateFlow(null)
            }
            return flow.asStateFlow()
        }

        override suspend fun saveContactBook(deviceId: String, contactBook: DeviceContactBook): Result<Unit> {
            val flow = storage.computeIfAbsent(deviceId) { MutableStateFlow(contactBook) }
            flow.value = contactBook
            return Result.success(Unit)
        }
    }

    private val repo: ContactRepository = TestContactRepository()

    @Test
    fun `getContactBook returns null before any sync`() = runTest {
        val book = repo.getContactBook("HOST-101").first()
        assertNull(book)
    }

    @Test
    fun `saveContactBook updates flow emission with real contacts`() = runTest {
        val sampleContacts = listOf(
            DeviceContact(name = "John Doe", phone = "+1 555-0100", type = "Mobile"),
            DeviceContact(name = "Mom ICE", phone = "+1 555-0999", type = "Mobile", isEmergency = true)
        )
        val book = DeviceContactBook(
            deviceId = "HOST-101",
            total = 2,
            contacts = sampleContacts,
            emergencyContact = sampleContacts[1],
            lastSynced = "Synced just now"
        )

        val result = repo.saveContactBook("HOST-101", book)
        assertTrue(result.isSuccess)

        val retrieved = repo.getContactBook("HOST-101").first()
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.total)
        assertEquals("John Doe", retrieved?.contacts?.get(0)?.name)
        assertEquals("+1 555-0100", retrieved?.contacts?.get(0)?.phone)
        assertEquals("Mom ICE", retrieved?.emergencyContact?.name)
    }
}
