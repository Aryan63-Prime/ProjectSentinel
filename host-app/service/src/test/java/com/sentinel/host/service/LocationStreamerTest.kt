package com.sentinel.host.service

import com.sentinel.host.domain.location.LocationProvider
import com.sentinel.host.domain.model.LocationConfig
import com.sentinel.host.domain.model.LocationUpdate
import com.sentinel.host.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationStreamerTest {

    private lateinit var testScope: TestScope
    private lateinit var fakeProvider: FakeLocationProvider
    private lateinit var fakeRepo: FakeLocationRepository
    private lateinit var streamer: LocationStreamer

    @Before
    fun setUp() {
        testScope = TestScope()
        fakeProvider = FakeLocationProvider()
        fakeRepo = FakeLocationRepository()

        streamer = LocationStreamer(
            locationProvider = fakeProvider,
            locationRepository = fakeRepo,
            scope = testScope
        )
    }

    // ================================================================
    // Permission handling
    // ================================================================

    @Test
    fun `start is no-op when permission denied`() {
        streamer.hasPermission = false
        streamer.start()

        assertFalse(fakeProvider.isActive)
        assertFalse(streamer.isStreaming)
    }

    @Test
    fun `start works when permission granted`() {
        streamer.hasPermission = true
        streamer.start()

        assertTrue(fakeProvider.isActive)
    }

    // ================================================================
    // Start / stop lifecycle
    // ================================================================

    @Test
    fun `stop cancels collection and provider`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(fakeProvider.isActive)

        streamer.stop()
        assertFalse(fakeProvider.isActive)
        assertFalse(streamer.isStreaming)
    }

    @Test
    fun `start is idempotent - no duplicate providers`() {
        streamer.hasPermission = true
        streamer.start()
        assertEquals(1, fakeProvider.startCount)

        streamer.start()
        // Should have stopped and restarted
        assertEquals(2, fakeProvider.startCount)
        assertTrue(fakeProvider.isActive)
    }

    @Test
    fun `stop is idempotent`() {
        streamer.stop()
        streamer.stop()
        assertFalse(fakeProvider.isActive)
    }

    // ================================================================
    // Location collection and sending
    // ================================================================

    @Test
    fun `location updates are sent to repository`() {
        streamer.hasPermission = true
        streamer.start()
        testScope.advanceUntilIdle() // Activate flow collector

        val update = LocationUpdate(37.7749, -122.4194, 10f, 85, "wifi")
        fakeProvider.emitLocation(update)
        testScope.advanceUntilIdle()

        assertEquals(1, fakeRepo.sentLocations.size)
        assertEquals(37.7749, fakeRepo.sentLocations.first().latitude, 0.001)
    }

    @Test
    fun `multiple locations are sent sequentially`() {
        streamer.hasPermission = true
        streamer.start()
        testScope.advanceUntilIdle() // Activate flow collector

        fakeProvider.emitLocation(LocationUpdate(1.0, 2.0, 5f, 90, "lte"))
        testScope.advanceUntilIdle()
        fakeProvider.emitLocation(LocationUpdate(3.0, 4.0, 8f, 88, "wifi"))
        testScope.advanceUntilIdle()

        assertEquals(2, fakeRepo.sentLocations.size)
    }

    // ================================================================
    // Pause / resume (reconnect)
    // ================================================================

    @Test
    fun `pause stops provider and collection`() {
        streamer.hasPermission = true
        streamer.start()
        assertTrue(fakeProvider.isActive)

        streamer.pause()
        assertFalse(fakeProvider.isActive)
    }

    @Test
    fun `resume restarts provider after pause`() {
        streamer.hasPermission = true
        streamer.start()
        streamer.pause()
        assertFalse(fakeProvider.isActive)

        streamer.resume()
        assertTrue(fakeProvider.isActive)
    }

    @Test
    fun `resume is no-op without permission`() {
        streamer.hasPermission = false
        streamer.resume()
        assertFalse(fakeProvider.isActive)
    }

    // ================================================================
    // Cached location
    // ================================================================

    @Test
    fun `lastLocation returns null initially`() {
        assertNull(streamer.lastLocation)
    }

    @Test
    fun `lastLocation returns latest after update`() {
        streamer.hasPermission = true
        streamer.start()
        testScope.advanceUntilIdle() // Activate flow collector

        val update = LocationUpdate(51.5074, -0.1278, 15f, 70, "5g")
        fakeProvider.emitLocation(update)
        testScope.advanceUntilIdle()

        assertNotNull(streamer.lastLocation)
        assertEquals(51.5074, streamer.lastLocation!!.latitude, 0.001)
    }

    // ================================================================
    // Ready state gating (tested via permission flag)
    // ================================================================

    @Test
    fun `no locations sent when not streaming`() {
        // Don't start the streamer
        fakeProvider.emitLocation(LocationUpdate(1.0, 2.0, 5f, 90, "lte"))
        testScope.advanceUntilIdle()

        assertTrue(fakeRepo.sentLocations.isEmpty())
    }
}

// ================================================================
// Test fakes
// ================================================================

internal class FakeLocationProvider : LocationProvider {
    private val _locations = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
    override val locations: Flow<LocationUpdate> = _locations

    @Volatile
    override var isActive: Boolean = false
        private set

    override var lastLocation: LocationUpdate? = null
        private set

    var startCount = 0
        private set

    override fun startUpdates(config: LocationConfig) {
        isActive = true
        startCount++
    }

    override fun stopUpdates() {
        isActive = false
    }

    fun emitLocation(update: LocationUpdate) {
        lastLocation = update
        _locations.tryEmit(update)
    }
}

internal class FakeLocationRepository : LocationRepository {
    val sentLocations = mutableListOf<LocationUpdate>()

    override suspend fun sendLocation(location: LocationUpdate) {
        sentLocations.add(location)
    }
}
