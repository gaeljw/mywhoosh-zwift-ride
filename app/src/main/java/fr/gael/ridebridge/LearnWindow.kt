package fr.gael.ridebridge

/** "Press the button for gear up" stays armed for a limited time, so a forgotten request can't hijack a later shift. */
class LearnWindow(private val timeoutMs: Long, private val clock: () -> Long) {
    private var direction: Direction? = null
    private var deadline = 0L

    fun set(value: Direction?) {
        direction = value
        deadline = clock() + timeoutMs
    }

    /** The direction being learned, or null if none or the window has elapsed. */
    fun current(): Direction? {
        if (direction != null && clock() >= deadline) direction = null
        return direction
    }
}
