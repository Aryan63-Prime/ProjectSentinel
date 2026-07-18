package com.sentinel.admin.data.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [JitterBuffer].
 *
 * Verifies ordering, duplicate rejection, late dropping, wrap-around,
 * adaptive depth, PLC gap detection, and statistics.
 */
class JitterBufferTest {

    private lateinit var buffer: JitterBuffer

    @Before
    fun setUp() {
        buffer = JitterBuffer(minDepth = 2, maxDepth = 5, maxLateDistance = 10)
    }

    // ============================================================
    // Basic operations
    // ============================================================

    @Test
    fun `push and pop in order`() {
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(1, byteArrayOf(0x02))

        val r1 = buffer.pop()
        assertTrue(r1 is JitterBuffer.PopResult.Frame)
        assertEquals(0L, (r1 as JitterBuffer.PopResult.Frame).sequence)

        val r2 = buffer.pop()
        assertTrue(r2 is JitterBuffer.PopResult.Frame)
        assertEquals(1L, (r2 as JitterBuffer.PopResult.Frame).sequence)

        val r3 = buffer.pop()
        assertTrue(r3 is JitterBuffer.PopResult.Empty)
    }

    @Test
    fun `out of order packets reordered by TreeMap`() {
        buffer.push(2, byteArrayOf(0x03))
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(1, byteArrayOf(0x02))

        // Pop should deliver 0, 1, 2 in order
        val r1 = buffer.pop() as JitterBuffer.PopResult.Frame
        assertEquals(0L, r1.sequence)

        val r2 = buffer.pop() as JitterBuffer.PopResult.Frame
        assertEquals(1L, r2.sequence)

        val r3 = buffer.pop() as JitterBuffer.PopResult.Frame
        assertEquals(2L, r3.sequence)
    }

    @Test
    fun `isReady after reaching target depth`() {
        assertFalse(buffer.isReady())
        buffer.push(0, byteArrayOf(0x01))
        assertFalse(buffer.isReady()) // 1 < minDepth(2)
        buffer.push(1, byteArrayOf(0x02))
        assertTrue(buffer.isReady()) // 2 >= minDepth(2)
    }

    // ============================================================
    // Duplicate rejection
    // ============================================================

    @Test
    fun `duplicate sequence rejected`() {
        val r1 = buffer.push(5, byteArrayOf(0x01))
        assertEquals(JitterBuffer.PushResult.ACCEPTED, r1)

        val r2 = buffer.push(5, byteArrayOf(0x02))
        assertEquals(JitterBuffer.PushResult.DUPLICATE, r2)

        assertEquals(1, buffer.droppedDuplicate)
        assertEquals(1, buffer.size())
    }

    // ============================================================
    // Late packet dropping
    // ============================================================

    @Test
    fun `late packets dropped`() {
        // Push and consume frames 0-15
        for (i in 0L..15L) {
            buffer.push(i, byteArrayOf(i.toByte()))
        }
        // Pop all to advance playback head
        repeat(16) { buffer.pop() }

        // Now playback head is at 16. Push sequence 2 (14 frames behind)
        val result = buffer.push(2, byteArrayOf(0x00))
        assertEquals(JitterBuffer.PushResult.LATE, result)
        assertEquals(1L, buffer.droppedLate)
    }

    // ============================================================
    // PLC (missing packet detection)
    // ============================================================

    @Test
    fun `missing packet returns Missing result for PLC`() {
        // Push 0 and 2, skip 1
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(2, byteArrayOf(0x03))

        // Pop 0
        val r1 = buffer.pop() as JitterBuffer.PopResult.Frame
        assertEquals(0L, r1.sequence)

        // Pop should detect gap at 1
        val r2 = buffer.pop()
        assertTrue(r2 is JitterBuffer.PopResult.Missing)
        assertEquals(1L, (r2 as JitterBuffer.PopResult.Missing).expectedSequence)

        // Next pop delivers 2
        val r3 = buffer.pop() as JitterBuffer.PopResult.Frame
        assertEquals(2L, r3.sequence)
    }

    // ============================================================
    // Wrap-around
    // ============================================================

    @Test
    fun `sequence distance handles normal case`() {
        assertEquals(5, JitterBuffer.sequenceDistance(10, 5))
        assertEquals(-5, JitterBuffer.sequenceDistance(5, 10))
    }

    @Test
    fun `nextSequence wraps around uint32`() {
        assertEquals(0L, JitterBuffer.nextSequence(0xFFFFFFFFL))
        assertEquals(1L, JitterBuffer.nextSequence(0L))
    }

    // ============================================================
    // Clear and reset
    // ============================================================

    @Test
    fun `clear resets buffer but keeps stats`() {
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(1, byteArrayOf(0x02))
        buffer.clear()

        assertEquals(0, buffer.size())
        assertFalse(buffer.isReady())
        assertEquals(2L, buffer.received) // stats preserved
    }

    @Test
    fun `reset clears everything including stats`() {
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(0, byteArrayOf(0x01)) // duplicate
        buffer.reset()

        assertEquals(0, buffer.size())
        assertEquals(0L, buffer.received)
        assertEquals(0L, buffer.droppedDuplicate)
    }

    // ============================================================
    // Statistics
    // ============================================================

    @Test
    fun `statistics track correctly`() {
        buffer.push(0, byteArrayOf(0x01))
        buffer.push(1, byteArrayOf(0x02))
        buffer.push(1, byteArrayOf(0x03)) // duplicate

        assertEquals(3L, buffer.received)
        assertEquals(1L, buffer.droppedDuplicate)

        buffer.pop()
        buffer.pop()
        assertEquals(2L, buffer.delivered)
    }

    // ============================================================
    // Adaptive depth
    // ============================================================

    @Test
    fun `jitter detection grows target depth`() {
        assertEquals(2, buffer.currentTargetDepth())

        // Push out of order to trigger jitter
        buffer.push(5, byteArrayOf(0x01))
        buffer.push(3, byteArrayOf(0x02)) // out of order → jitter

        assertTrue(buffer.currentTargetDepth() > 2)
    }
}
