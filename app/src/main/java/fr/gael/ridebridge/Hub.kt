package fr.gael.ridebridge

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

enum class Direction { UP, DOWN }

/** Small in-process state shared by the service, the activity and the overlay. Read on the main thread. */
object Hub {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()

    var status = "Stopped"; private set
    var running = false; private set
    var connected = false; private set
    var firmware: String? = null; private set
    var lastEvent = ""; private set

    private const val LEARN_TIMEOUT_MS = 10_000L
    private val learnWindow = LearnWindow(LEARN_TIMEOUT_MS) { SystemClock.elapsedRealtime() }
    private val learnExpired = Runnable { notifyListeners() }

    /** When set, the next controller button press is assigned to this direction instead of shifting. Expires on its own. */
    var learning: Direction?
        get() = learnWindow.current()
        set(value) {
            learnWindow.set(value)
            main.removeCallbacks(learnExpired)
            if (value != null) main.postDelayed(learnExpired, LEARN_TIMEOUT_MS)
            notifyListeners()
        }

    fun update(
        status: String? = null,
        running: Boolean? = null,
        connected: Boolean? = null,
        firmware: String? = null,
        lastEvent: String? = null,
    ) {
        main.post { applyUpdate(status, running, connected, firmware, lastEvent) }
    }

    private fun applyUpdate(status: String?, running: Boolean?, connected: Boolean?, firmware: String?, lastEvent: String?) {
        status?.let { this.status = it }
        running?.let { this.running = it }
        connected?.let { this.connected = it }
        firmware?.let { this.firmware = it }
        lastEvent?.let { this.lastEvent = it }
        notifyListeners()
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() = listeners.toList().forEach { it() }
}
