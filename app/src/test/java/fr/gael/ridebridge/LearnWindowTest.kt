package fr.gael.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnWindowTest {
    private var now = 1_000L
    private val window = LearnWindow(10_000) { now }

    @Test
    fun idleByDefault() {
        assertNull(window.current())
    }

    @Test
    fun staysArmedWithinTheTimeout() {
        window.set(Direction.UP)
        now += 9_999
        assertEquals(Direction.UP, window.current())
    }

    @Test
    fun expiresAfterTheTimeout() {
        window.set(Direction.DOWN)
        now += 10_000
        assertNull(window.current())
        now += 1
        assertNull(window.current())
    }

    @Test
    fun canBeCancelled() {
        window.set(Direction.UP)
        window.set(null)
        assertNull(window.current())
    }

    @Test
    fun rearmingRestartsTheTimeout() {
        window.set(Direction.UP)
        now += 8_000
        window.set(Direction.DOWN)
        now += 8_000
        assertEquals(Direction.DOWN, window.current())
    }
}
