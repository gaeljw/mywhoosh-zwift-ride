package fr.gael.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.rules.Timeout

class RideKeyPadTest {
    // A parser stuck in a loop must fail the test instead of hanging the build.
    @get:Rule
    val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun readsButtonMapField() {
        // opcode, field 1 varint 0xFFFD (left hood ▲ pressed, active-low)
        assertEquals(0xFFFD, RideKeyPad.buttonMap(bytes(0x23, 0x08, 0xFD, 0xFF, 0x03), 1))
    }

    @Test
    fun skipsOtherFieldsBeforeButtonMap() {
        // field 2 length-delimited (2 bytes), then field 1
        assertEquals(5, RideKeyPad.buttonMap(bytes(0x23, 0x12, 0x02, 0xAA, 0xBB, 0x08, 0x05), 1))
    }

    @Test
    fun returnsNullWhenButtonMapAbsent() {
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x10, 0x01), 1))
    }

    @Test
    fun rejectsLengthThatWouldLoopForever() {
        // length varint decodes to 0xFFFFFFFA, whose toInt() (-6) sends the cursor back to the same tag
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x12, 0xFA, 0xFF, 0xFF, 0xFF, 0x0F), 1))
    }

    @Test
    fun rejectsNegativeLength() {
        // 10-byte varint = -1 as a Long
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x12, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01), 1))
    }

    @Test
    fun rejectsLengthPastTheEndOfTheFrame() {
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x12, 0x09, 0xAA, 0x08, 0x05), 1))
    }

    @Test
    fun handlesTruncatedAndEmptyFrames() {
        assertNull(RideKeyPad.buttonMap(bytes(0x23), 1))
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x08), 1))
        assertNull(RideKeyPad.buttonMap(bytes(0x23, 0x12), 1))
    }
}
