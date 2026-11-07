package fr.gael.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RideButtonTest {
    @Test
    fun labelsListsEveryButtonInTheMask() {
        assertEquals("Left shift up, Left shift down", RideButton.labels(0x0300))
    }

    @Test
    fun labelsFallsBackToHexForUnknownMask() {
        assertEquals("0x10000", RideButton.labels(0x10000))
    }
}
