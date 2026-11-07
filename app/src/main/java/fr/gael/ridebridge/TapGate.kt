package fr.gael.ridebridge

/**
 * Decides whether a shift may be turned into a tap. The saved coordinates only mean something inside MyWhoosh:
 * anywhere else (another app, the notification shade, the lock screen, our own screens) a tap could press anything.
 */
class TapGate(private val allowedPackage: String = MYWHOOSH_PACKAGE) {

    companion object {
        // Check with: adb shell pm list packages | grep -i whoosh
        const val MYWHOOSH_PACKAGE = "com.mywhoosh.whooshgame"
    }

    private var foreground: String? = null
    var overlayOpen = false

    /** Fed with the package of each window-state-changed accessibility event. */
    fun onWindowChanged(packageName: CharSequence?) {
        if (packageName != null) foreground = packageName.toString()
    }

    fun reset() {
        foreground = null
        overlayOpen = false
    }

    /** Null when tapping is allowed, otherwise a short reason to show the user. */
    fun blockedReason(): String? = when {
        overlayOpen -> "calibration overlay is open"
        foreground == null -> "MyWhoosh is not in front (open it, then shift)"
        foreground != allowedPackage -> "MyWhoosh is not in front ($foreground is)"
        else -> null
    }
}
