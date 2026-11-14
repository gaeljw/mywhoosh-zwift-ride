package fr.gael.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class PressedUnionTest {
    @Test
    fun reportsAPressOnce() {
        val u = PressedUnion()
        assertEquals(0x1000, u.update(Hood.RIGHT, 0x1000))
        assertEquals(0, u.update(Hood.RIGHT, 0x1000))
    }

    @Test
    fun aButtonReportedByBothHoodsCountsOnce() {
        val u = PressedUnion()
        assertEquals(0x1000, u.update(Hood.LEFT, 0x1000))
        assertEquals(0, u.update(Hood.RIGHT, 0x1000))
        // Released by the left hood only: the right one still holds it, so it is not a new press.
        assertEquals(0, u.update(Hood.LEFT, 0))
        assertEquals(0, u.update(Hood.LEFT, 0x1000))
    }

    @Test
    fun hoodsAreIndependent() {
        val u = PressedUnion()
        assertEquals(0x0100, u.update(Hood.LEFT, 0x0100))
        assertEquals(0x2000, u.update(Hood.RIGHT, 0x2000))
    }

    @Test
    fun disconnectedHoodReleasesItsButtons() {
        val u = PressedUnion()
        u.update(Hood.RIGHT, 0x1000)
        u.clear(Hood.RIGHT)
        assertEquals(0x1000, u.update(Hood.RIGHT, 0x1000))
    }
}
