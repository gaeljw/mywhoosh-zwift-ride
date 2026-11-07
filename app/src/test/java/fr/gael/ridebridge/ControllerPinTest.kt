package fr.gael.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPinTest {
    @Test
    fun acceptsAnyDeviceWhileNothingIsPinned() {
        assertTrue(ControllerPin.accepts(null, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun acceptsOnlyThePinnedDevice() {
        assertTrue(ControllerPin.accepts("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
        assertTrue(ControllerPin.accepts("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff"))
        assertFalse(ControllerPin.accepts("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"))
    }

    @Test
    fun pinsTheFirstConnectedDeviceOnly() {
        assertEquals("AA:BB:CC:DD:EE:FF", ControllerPin.toPin(null, "AA:BB:CC:DD:EE:FF"))
        assertNull(ControllerPin.toPin("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
        assertNull(ControllerPin.toPin("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"))
    }
}
