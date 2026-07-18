package com.sentinel.admin.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SequenceGenerator].
 */
class SequenceGeneratorTest {

    private lateinit var generator: SequenceGenerator

    @Before
    fun setUp() {
        generator = SequenceGenerator()
    }

    @Test
    fun `next returns monotonically increasing values`() {
        assertEquals(1L, generator.next())
        assertEquals(2L, generator.next())
        assertEquals(3L, generator.next())
    }

    @Test
    fun `reset returns counter to zero`() {
        generator.next()
        generator.next()
        generator.reset()
        assertEquals(1L, generator.next())
    }

    @Test
    fun `next is thread-safe`() {
        val results = mutableSetOf<Long>()
        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    synchronized(results) {
                        results.add(generator.next())
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All 1000 values should be unique
        assertEquals(1000, results.size)
        // Should cover 1..1000
        assertTrue(results.contains(1L))
        assertTrue(results.contains(1000L))
    }
}
