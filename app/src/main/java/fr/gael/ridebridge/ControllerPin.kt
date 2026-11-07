package fr.gael.ridebridge

/**
 * Trust on first use: the first Zwift Ride that completes the handshake is remembered by address, and nothing else
 * is connected to afterwards (a neighbour's Ride, or a device imitating one). "Forget controller" starts over.
 */
object ControllerPin {
    /** Whether a scan result may be connected to. */
    fun accepts(pinned: String?, address: String): Boolean = pinned == null || pinned.equals(address, ignoreCase = true)

    /** The address to remember after a successful connection, or null if one is already remembered. */
    fun toPin(pinned: String?, connected: String): String? = if (pinned == null) connected else null
}
