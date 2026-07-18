package com.sentinel.host.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SequenceGeneratorTest {

    private lateinit var generator: SequenceGenerator

    @Before
    fun setUp() {
        generator = SequenceGenerator()
    }

    @Test
    fun `first sequence is 1`() {
        assertEquals(1L, generator.next())
    }

    @Test
    fun `sequences are monotonically increasing`() {
        val first = generator.next()
        val second = generator.next()
        val third = generator.next()
        assertTrue(second > first)
        assertTrue(third > second)
    }

    @Test
    fun `reset sets counter back to 0`() {
        generator.next()
        generator.next()
        generator.reset()
        assertEquals(1L, generator.next())
    }

    @Test
    fun `concurrent calls produce unique values`() {
        val results = (1..1000).map { generator.next() }.toSet()
        assertEquals(1000, results.size)
    }
}
