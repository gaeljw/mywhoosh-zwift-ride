package fr.gael.ridebridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Accessibility service: performs the taps on MyWhoosh and hosts the calibration overlay. */
class TapService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TapService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: CalibrationOverlay? = null

    override fun onServiceConnected() {
        instance = this
        Hub.update()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        overlay?.dismiss()
        Hub.update()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun tap(p: TapPoint): Boolean {
        val (x, y) = Screen.adjust(this, p)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Shows the overlay after a delay, leaving time to switch to MyWhoosh. */
    fun startCalibration(delayMs: Long) {
        main.postDelayed({
            if (overlay == null) {
                overlay = CalibrationOverlay(this) { overlay = null }.also { it.show() }
            }
        }, delayMs)
    }
}
