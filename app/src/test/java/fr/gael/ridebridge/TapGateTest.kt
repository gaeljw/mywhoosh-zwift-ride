package fr.gael.ridebridge

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {
    private val pkg = "com.example.game"
    private fun gate() = TapGate(pkg)

    @Test
    fun blocksUntilAWindowHasBeenSeen() {
        assertNotNull(gate().blockedReason())
    }

    @Test
    fun allowsWhenGameIsInFront() {
        val g = gate().apply { onWindowChanged(pkg) }
        assertNull(g.blockedReason())
    }

    @Test
    fun blocksWhenAnotherAppTakesOver() {
        val g = gate().apply { onWindowChanged(pkg); onWindowChanged("com.android.systemui") }
        assertTrue(g.blockedReason()!!.contains("com.android.systemui"))
    }

    @Test
    fun allowsAgainWhenGameReturns() {
        val g = gate().apply { onWindowChanged("com.other"); onWindowChanged(pkg) }
        assertNull(g.blockedReason())
    }

    @Test
    fun eventsWithoutPackageDoNotChangeTheState() {
        val g = gate().apply { onWindowChanged(pkg); onWindowChanged(null) }
        assertNull(g.blockedReason())
    }

    @Test
    fun blocksWhileTheCalibrationOverlayIsOpen() {
        val g = gate().apply { onWindowChanged(pkg); overlayOpen = true }
        assertNotNull(g.blockedReason())
        g.overlayOpen = false
        assertNull(g.blockedReason())
    }

    @Test
    fun resetBlocksAgain() {
        val g = gate().apply { onWindowChanged(pkg); reset() }
        assertNotNull(g.blockedReason())
    }
}
